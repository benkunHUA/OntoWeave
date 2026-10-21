package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineScheduleWorkerTest {

    private final PipelineScheduleClaimer claimer = mock(PipelineScheduleClaimer.class);
    private final PipelineScheduleService service = mock(PipelineScheduleService.class);
    private final PipelineScheduleProperties properties = new PipelineScheduleProperties();

    @Test
    void pollReturnsImmediatelyWhileDispatchRunsOnItsOwnWorkerThread() throws Exception {
        properties.setExecutorThreads(1);
        properties.setExecutorQueueCapacity(0);
        properties.setMaxPerPoll(1);
        PipelineScheduleEntity schedule = schedule();
        Instant dueAt = Instant.parse("2026-03-07T10:00:00Z");
        when(claimer.claimDue(any(), anyInt())).thenReturn(List.of(
                new PipelineScheduleClaimer.DueSchedule(schedule, dueAt)
        ));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        when(service.dispatch(schedule, dueAt)).thenAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            started.countDown();
            release.await(10, TimeUnit.SECONDS);
            return null;
        });
        PipelineScheduleWorker worker = new PipelineScheduleWorker(claimer, service, properties);

        try {
            long before = System.nanoTime();
            worker.poll();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

            assertThat(elapsedMillis).isLessThan(1_000);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).startsWith("pipeline-schedule-");
        } finally {
            release.countDown();
            worker.shutdown();
        }
    }

    @Test
    void shutdownStopsNewPollsFromClaimingWork() {
        properties.setExecutorThreads(1);
        properties.setExecutorQueueCapacity(0);
        properties.setMaxPerPoll(1);
        PipelineScheduleWorker worker = new PipelineScheduleWorker(claimer, service, properties);

        worker.shutdown();
        worker.poll();

        verify(claimer, never()).claimDue(any(), anyInt());
    }

    private PipelineScheduleEntity schedule() {
        return new PipelineScheduleEntity(
                UUID.randomUUID(), UUID.randomUUID(), "schedule", "* * * * *", "UTC",
                Instant.parse("2026-03-07T10:00:00Z"), UUID.randomUUID()
        );
    }
}
