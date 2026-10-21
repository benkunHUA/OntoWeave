package com.ontoweave.data.pipeline;

import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.schedule.PipelineScheduleClaimer;
import com.ontoweave.data.pipeline.schedule.ScheduleRequest;
import com.ontoweave.data.pipeline.schedule.ScheduleRunResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PipelineSchedulePersistenceIntegrationTest extends PipelineScheduleIntegrationTestSupport {

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void claimPersistsPendingSoACrashBeforeDispatchCannotLoseTheTrigger() {
        PublishedPipeline published = publishedPipeline();
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), dueAt);

        claimer.claimDue(dueAt.plusSeconds(30), 100);

        assertThat(pendingCount(scheduleId)).isEqualTo(1);
        assertThat(instant("""
                SELECT scheduled_for FROM data.pipeline_schedule_run
                WHERE schedule_id = ? AND status = 'pending'
                """, scheduleId)).isEqualTo(dueAt);
        assertThat(count("data.pipeline_build")).isZero();

        ScheduleRunResponse recovered = schedules.dispatch(schedule(scheduleId), dueAt);

        assertThat(recovered.status()).isEqualTo("dispatched");
        assertThat(recovered.scheduledFor()).isEqualTo(dueAt);
        assertThat(count("data.pipeline_build")).isEqualTo(1);
        assertThat(pendingCount(scheduleId)).isZero();
    }

    @Test
    void transientDispatchFailureKeepsThePersistedPendingRunForRetry() {
        PublishedPipeline published = publishedPipeline();
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), dueAt);
        claimer.claimDue(dueAt.plusSeconds(30), 100);
        when(inputs.resolve(any(PipelineVersionEntity.class)))
                .thenThrow(new IllegalStateException("input registry temporarily unavailable"));

        assertThatThrownBy(() -> schedules.dispatch(schedule(scheduleId), dueAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("temporarily unavailable");

        assertThat(pendingCount(scheduleId)).isEqualTo(1);
        assertThat(count("data.pipeline_build")).isZero();

        when(inputs.resolve(any(PipelineVersionEntity.class))).thenReturn(List.of());
        ScheduleRunResponse retried = schedules.dispatch(schedule(scheduleId), dueAt);

        assertThat(retried.status()).isEqualTo("dispatched");
        assertThat(count("data.pipeline_build")).isEqualTo(1);
    }

    @Test
    void aClaimMergesIntoAnExistingPendingWithoutCreatingASecondRun() {
        PublishedPipeline published = publishedPipeline();
        Instant firstDue = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), firstDue);
        insertRun(scheduleId, published.pipelineId(), firstDue.toString(), "pending");

        claimer.claimDue(firstDue.plusSeconds(90), 100);

        assertThat(pendingCount(scheduleId)).isEqualTo(1);
        assertThat(count("data.pipeline_schedule_run")).isEqualTo(1);
        assertThat(instant("SELECT next_run_at FROM data.pipeline_schedule WHERE id = ?", scheduleId))
                .isEqualTo(Instant.parse("2026-03-07T10:02:00Z"));
        assertThat(instant("SELECT last_run_at FROM data.pipeline_schedule WHERE id = ?", scheduleId))
                .isEqualTo(Instant.parse("2026-03-07T10:01:00Z"));
    }

    @Test
    void pendingScanSkipsAScheduleLockedByAnotherWorker() throws Exception {
        PublishedPipeline published = publishedPipeline();
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), dueAt);
        claimer.claimDue(dueAt.plusSeconds(30), 100);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        jdbc.queryForObject(
                                "SELECT id FROM data.pipeline_schedule WHERE id = ? FOR UPDATE",
                                UUID.class, scheduleId
                        );
                        locked.countDown();
                        await(release);
                    }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(claimer.claimPending(100)).isEmpty();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertThat(claimer.claimPending(100))
                    .extracting(PipelineScheduleClaimer.PendingSchedule::dueAt)
                    .containsExactly(dueAt);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void pendingRunIsPickedUpAgainAfterAnActiveBuildFinishes() {
        PublishedPipeline published = publishedPipeline();
        UUID activeBuild = insertBuild(
                published.pipelineId(), published.versionId(),
                PipelineBuildStatus.QUEUED, UUID.randomUUID()
        );
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), dueAt);
        claimer.claimDue(dueAt.plusSeconds(30), 100);

        PipelineScheduleClaimer.PendingSchedule first = claimer.claimPending(100).get(0);
        ScheduleRunResponse waiting = schedules.dispatch(first.schedule(), first.dueAt());

        assertThat(waiting.status()).isEqualTo("pending");
        assertThat(pendingCount(scheduleId)).isEqualTo(1);

        jdbc.update(
                "UPDATE data.pipeline_build SET status = 'succeeded', finished_at = now() WHERE id = ?",
                activeBuild
        );
        PipelineScheduleClaimer.PendingSchedule recovered = claimer.claimPending(100).get(0);
        ScheduleRunResponse dispatched = schedules.dispatch(
                recovered.schedule(), recovered.dueAt()
        );

        assertThat(dispatched.status()).isEqualTo("dispatched");
        assertThat(dispatched.scheduledFor()).isEqualTo(dueAt);
        assertThat(pendingCount(scheduleId)).isZero();
        assertThat(count("data.pipeline_build")).isEqualTo(2);
    }

    @Test
    void putKeepsAPausedSchedulePausedAndOnlyResumeReactivatesIt() {
        PublishedPipeline published = publishedPipeline();
        schedules.put(published.pipelineId(), ownerId,
                new ScheduleRequest("*/5 * * * *", "UTC", "before"));
        schedules.pause(published.pipelineId(), ownerId);

        var updated = schedules.put(
                published.pipelineId(), ownerId,
                new ScheduleRequest("0 9 * * *", "Asia/Shanghai", "after")
        );

        assertThat(updated.status()).isEqualTo("paused");
        assertThat(updated.name()).isEqualTo("after");
        assertThat(updated.cronExpression()).isEqualTo("0 9 * * *");
        assertThat(updated.nextRunAt()).isNull();

        var resumed = schedules.resume(published.pipelineId(), ownerId);
        assertThat(resumed.status()).isEqualTo("active");
        assertThat(resumed.nextRunAt()).isNotNull();
    }

    @Test
    void lastRunAtNeverMovesBackwardsWhenAWorkerClaimsAnOlderTrigger() {
        PublishedPipeline published = publishedPipeline();
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), dueAt);
        Instant newer = dueAt.plusSeconds(3600);
        jdbc.update(
                "UPDATE data.pipeline_schedule SET last_run_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(newer, ZoneOffset.UTC), scheduleId
        );

        claimer.claimDue(dueAt.plusSeconds(1), 100);

        assertThat(instant("SELECT last_run_at FROM data.pipeline_schedule WHERE id = ?", scheduleId))
                .isEqualTo(newer);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
