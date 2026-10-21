package com.ontoweave.data.pipeline;

import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.schedule.PipelineScheduleClaimer;
import com.ontoweave.data.pipeline.schedule.ScheduleLogicHash;
import com.ontoweave.data.pipeline.schedule.ScheduleOperationIds;
import com.ontoweave.data.pipeline.schedule.ScheduleRunResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PipelineScheduleIntegrationTest extends PipelineScheduleIntegrationTestSupport {

    @Test
    void concurrentClaimersAdvanceEachDueScheduleExactlyOnce() throws Exception {
        PublishedPipeline published = publishedPipeline();
        Instant now = Instant.parse("2026-03-07T10:05:30Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), now.minusSeconds(120));

        List<List<PipelineScheduleClaimer.DueSchedule>> outcomes = runConcurrently(
                () -> claimer.claimDue(now, 100),
                () -> claimer.claimDue(now, 100)
        );

        assertThat(outcomes.stream().mapToInt(List::size).sum()).isEqualTo(1);
        assertThat(instant("SELECT next_run_at FROM data.pipeline_schedule WHERE id = ?", scheduleId))
                .isEqualTo(Instant.parse("2026-03-07T10:06:00Z"));
        assertThat(instant("SELECT last_run_at FROM data.pipeline_schedule WHERE id = ?", scheduleId))
                .isEqualTo(Instant.parse("2026-03-07T10:05:00Z"));
        assertThat(claimer.claimDue(now, 100)).isEmpty();
    }

    @Test
    void concurrentDispatchForTheSameDueTimeReturnsOneBuildAndOneRun() throws Exception {
        PublishedPipeline published = publishedPipeline();
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), dueAt);
        PipelineScheduleEntity schedule = schedule(scheduleId);

        List<ScheduleRunResponse> outcomes = runConcurrently(
                () -> schedules.dispatch(schedule, dueAt),
                () -> schedules.dispatch(schedule, dueAt)
        );

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0).id()).isEqualTo(outcomes.get(1).id());
        assertThat(outcomes.get(0).buildId()).isNotNull();
        assertThat(outcomes.get(0).status()).isEqualTo("dispatched");
        assertThat(count("data.pipeline_build")).isEqualTo(1);
        assertThat(count("data.pipeline_schedule_run")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT operation_id FROM data.pipeline_build", UUID.class
        )).isEqualTo(ScheduleOperationIds.forRun(scheduleId, dueAt));
    }

    @Test
    void activeBuildKeepsOnePendingRunAndDispatchesItAfterTheBuildFinishes() {
        PublishedPipeline published = publishedPipeline();
        UUID activeBuild = insertBuild(
                published.pipelineId(), published.versionId(),
                PipelineBuildStatus.QUEUED, UUID.randomUUID()
        );
        Instant firstDue = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), firstDue);

        ScheduleRunResponse first = schedules.dispatch(schedule(scheduleId), firstDue);
        ScheduleRunResponse second = schedules.dispatch(schedule(scheduleId), firstDue.plusSeconds(60));

        assertThat(first.status()).isEqualTo("pending");
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.scheduledFor()).isEqualTo(firstDue);
        assertThat(pendingCount(scheduleId)).isEqualTo(1);
        assertThat(count("data.pipeline_build")).isEqualTo(1);

        jdbc.update("UPDATE data.pipeline_build SET status = 'succeeded', finished_at = now() WHERE id = ?", activeBuild);
        ScheduleRunResponse dispatched = schedules.dispatch(schedule(scheduleId), firstDue.plusSeconds(120));

        assertThat(dispatched.status()).isEqualTo("dispatched");
        assertThat(dispatched.scheduledFor()).isEqualTo(firstDue);
        assertThat(count("data.pipeline_build")).isEqualTo(2);
        assertThat(pendingCount(scheduleId)).isZero();
    }

    @Test
    void pauseIgnoresPendingRunsWithoutCancelingAnActiveBuild() {
        PublishedPipeline published = publishedPipeline();
        UUID activeBuild = insertBuild(
                published.pipelineId(), published.versionId(),
                PipelineBuildStatus.RUNNING, UUID.randomUUID()
        );
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), dueAt);
        schedules.dispatch(schedule(scheduleId), dueAt);

        schedules.pause(published.pipelineId(), ownerId);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_schedule WHERE id = ?", String.class, scheduleId
        )).isEqualTo("paused");
        assertThat(jdbc.queryForObject(
                "SELECT reason_code FROM data.pipeline_schedule_run WHERE schedule_id = ?",
                String.class, scheduleId
        )).isEqualTo("SCHEDULE_PAUSED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, activeBuild
        )).isEqualTo("running");
        assertThat(count("data.pipeline_build")).isEqualTo(1);
    }

    @Test
    void concurrentPauseAndDispatchNeverLeavePendingWork() throws Exception {
        PublishedPipeline published = publishedPipeline();
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), dueAt);
        PipelineScheduleEntity schedule = schedule(scheduleId);

        runConcurrently(
                () -> {
                    schedules.dispatch(schedule, dueAt);
                    return Boolean.TRUE;
                },
                () -> {
                    schedules.pause(published.pipelineId(), ownerId);
                    return Boolean.TRUE;
                }
        );

        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_schedule WHERE id = ?", String.class, scheduleId
        )).isEqualTo("paused");
        assertThat(pendingCount(scheduleId)).isZero();
        int builds = count("data.pipeline_build");
        assertThat(builds).isLessThanOrEqualTo(1);
        if (builds == 1) {
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM data.pipeline_schedule_run WHERE schedule_id = ?",
                    String.class, scheduleId
            )).isEqualTo("dispatched");
        } else {
            assertThat(jdbc.queryForObject(
                    "SELECT reason_code FROM data.pipeline_schedule_run WHERE schedule_id = ?",
                    String.class, scheduleId
            )).isEqualTo("SCHEDULE_PAUSED");
        }
    }

    @Test
    void ignoresOnlyWhenTheLatestTerminalBuildSucceededWithTheSameLogicHash() {
        PublishedPipeline published = publishedPipeline();
        ResolvedInput input = resolvedInput();
        when(inputs.resolve(any(PipelineVersionEntity.class))).thenReturn(List.of(input));
        String hash = ScheduleLogicHash.compute(published.definitionHash(), List.of(input));
        UUID historical = insertBuild(
                published.pipelineId(), published.versionId(), PipelineBuildStatus.SUCCEEDED, UUID.randomUUID()
        );
        jdbc.update("UPDATE data.pipeline_build SET logic_hash = ? WHERE id = ?", hash, historical);
        Instant firstDue = Instant.parse("2026-03-07T10:00:00Z");
        UUID scheduleId = insertSchedule(published.pipelineId(), firstDue);

        ScheduleRunResponse ignored = schedules.dispatch(schedule(scheduleId), firstDue);

        assertThat(ignored.status()).isEqualTo("ignored");
        assertThat(ignored.reasonCode()).isEqualTo("NO_CHANGE");
        assertThat(count("data.pipeline_build")).isEqualTo(1);

        jdbc.update("UPDATE data.pipeline_build SET status = 'failed' WHERE id = ?", historical);
        ScheduleRunResponse dispatched = schedules.dispatch(schedule(scheduleId), firstDue.plusSeconds(60));

        assertThat(dispatched.status()).isEqualTo("dispatched");
        assertThat(count("data.pipeline_build")).isEqualTo(2);
    }

    @Test
    void runsArePagedByScheduledTimeAndRejectAnotherOwner() {
        PublishedPipeline published = publishedPipeline();
        UUID scheduleId = insertSchedule(published.pipelineId(), Instant.parse("2026-03-07T10:00:00Z"));
        insertRun(scheduleId, published.pipelineId(), "2026-03-07T10:00:00Z", "ignored");
        insertRun(scheduleId, published.pipelineId(), "2026-03-07T11:00:00Z", "ignored");
        insertRun(scheduleId, published.pipelineId(), "2026-03-07T12:00:00Z", "ignored");

        List<ScheduleRunResponse> page = schedules.runs(published.pipelineId(), ownerId, 1, 1);

        assertThat(page).singleElement().satisfies(run ->
                assertThat(run.scheduledFor()).isEqualTo(Instant.parse("2026-03-07T11:00:00Z")));
        assertThatThrownBy(() -> schedules.get(published.pipelineId(), UUID.randomUUID()))
                .hasMessageContaining("流程不存在");
    }
}
