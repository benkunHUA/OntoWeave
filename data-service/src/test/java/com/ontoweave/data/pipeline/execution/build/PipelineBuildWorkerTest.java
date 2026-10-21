package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.timeout;

class PipelineBuildWorkerTest {

    private static final UUID BUILD_ID = UUID.randomUUID();

    @Test
    void pollReturnsWhileBuildRunsOnAWorkerThread() throws Exception {
        PipelineBuildQueue queue = mock(PipelineBuildQueue.class);
        PipelineBuildExecutor executor = mock(PipelineBuildExecutor.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            finished.countDown();
            return null;
        }).when(executor).execute(BUILD_ID);
        when(queue.recoverLost(org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(queue.claimNext()).thenReturn(BUILD_ID, (UUID) null);
        PipelineBuildWorker worker = worker(queue, executor, mock(PipelineDatasetWriter.class), properties());

        long start = System.nanoTime();
        worker.poll();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMillis).isLessThan(1_000L);
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).startsWith("pipeline-build-");
        release.countDown();
        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        worker.shutdown();
    }

    @Test
    void fullExecutorDoesNotBlockPollOrClaimAnotherBuild() throws Exception {
        PipelineBuildQueue queue = mock(PipelineBuildQueue.class);
        PipelineBuildExecutor executor = mock(PipelineBuildExecutor.class);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        doAnswer(invocation -> {
            try {
                release.await(5, TimeUnit.SECONDS);
                return null;
            } finally {
                finished.countDown();
            }
        }).when(executor).execute(BUILD_ID);
        when(queue.recoverLost(org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(queue.claimNext()).thenReturn(BUILD_ID);
        PipelineBuildProperties properties = properties();
        properties.setExecutorThreads(1);
        properties.setExecutorQueueCapacity(0);
        PipelineBuildWorker worker = worker(queue, executor, mock(PipelineDatasetWriter.class), properties);

        worker.poll();
        org.mockito.Mockito.clearInvocations(queue);
        long start = System.nanoTime();
        worker.poll();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMillis).isLessThan(1_000L);
        verify(queue, never()).claimNext();
        release.countDown();
        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        worker.shutdown();
    }

    @Test
    void pollReturnsBeforeSlowRecoveryFinishes() throws Exception {
        PipelineBuildQueue queue = mock(PipelineBuildQueue.class);
        PipelineBuildExecutor executor = mock(PipelineBuildExecutor.class);
        CountDownLatch recoveryStarted = new CountDownLatch(1);
        CountDownLatch releaseRecovery = new CountDownLatch(1);
        CountDownLatch recoveryFinished = new CountDownLatch(1);
        doAnswer(invocation -> {
            try {
                recoveryStarted.countDown();
                releaseRecovery.await(5, TimeUnit.SECONDS);
                return 0;
            } finally {
                recoveryFinished.countDown();
            }
        }).when(queue).recoverLost(org.mockito.ArgumentMatchers.any());
        when(queue.claimNext()).thenReturn(null);
        PipelineBuildWorker worker = worker(queue, executor, mock(PipelineDatasetWriter.class), properties());

        long start = System.nanoTime();
        worker.poll();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMillis).isLessThan(1_000L);
        assertThat(recoveryStarted.await(2, TimeUnit.SECONDS)).isTrue();
        releaseRecovery.countDown();
        assertThat(recoveryFinished.await(2, TimeUnit.SECONDS)).isTrue();
        worker.shutdown();
    }

    @Test
    void shutdownStopsFurtherPolling() {
        PipelineBuildQueue queue = mock(PipelineBuildQueue.class);
        PipelineBuildExecutor executor = mock(PipelineBuildExecutor.class);
        PipelineBuildWorker worker = worker(queue, executor, mock(PipelineDatasetWriter.class), properties());

        worker.shutdown();
        worker.poll();

        verify(queue, never()).recoverLost(org.mockito.ArgumentMatchers.any());
        verify(queue, never()).claimNext();
    }

    @Test
    void slowCancellationOfOneBuildDoesNotBlockAnotherHeartbeat() throws Exception {
        UUID slowBuild = UUID.randomUUID();
        UUID otherBuild = UUID.randomUUID();
        PipelineBuildQueue queue = mock(PipelineBuildQueue.class);
        PipelineDatasetWriter writer = mock(PipelineDatasetWriter.class);
        CountDownLatch cancelStarted = new CountDownLatch(1);
        CountDownLatch releaseCancel = new CountDownLatch(1);
        CountDownLatch retriedCancel = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger cancelCalls =
                new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            int call = cancelCalls.incrementAndGet();
            if (call == 1) {
                cancelStarted.countDown();
                releaseCancel.await(5, TimeUnit.SECONDS);
            } else if (call == 2) {
                retriedCancel.countDown();
            }
            return null;
        }).when(writer).cancel(slowBuild);
        when(queue.heartbeat(slowBuild)).thenReturn(true);
        when(queue.heartbeat(otherBuild)).thenReturn(false);
        PipelineBuildWorker worker = worker(
                queue, mock(PipelineBuildExecutor.class), writer, properties()
        );

        long firstStart = System.nanoTime();
        worker.heartbeat(slowBuild);
        long firstElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - firstStart);
        assertThat(firstElapsed).isLessThan(500L);
        assertThat(cancelStarted.await(2, TimeUnit.SECONDS)).isTrue();

        worker.heartbeat(slowBuild);
        verify(writer, times(1)).cancel(slowBuild);

        long secondStart = System.nanoTime();
        worker.heartbeat(otherBuild);
        long secondElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - secondStart);
        assertThat(secondElapsed).isLessThan(500L);
        verify(queue).heartbeat(otherBuild);

        releaseCancel.countDown();
        long retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (retriedCancel.getCount() > 0 && System.nanoTime() < retryDeadline) {
            worker.heartbeat(slowBuild);
            Thread.sleep(10);
        }
        assertThat(retriedCancel.await(1, TimeUnit.SECONDS)).isTrue();
        verify(writer, times(2)).cancel(slowBuild);
        worker.shutdown();
    }

    @Test
    void failedCancellationIsRetriedByTheNextHeartbeat() {
        UUID buildId = UUID.randomUUID();
        PipelineBuildQueue queue = mock(PipelineBuildQueue.class);
        PipelineDatasetWriter writer = mock(PipelineDatasetWriter.class);
        when(queue.heartbeat(buildId)).thenReturn(true);
        doAnswer(invocation -> {
            throw new IllegalStateException("flink unavailable");
        }).doNothing().when(writer).cancel(buildId);
        PipelineBuildWorker worker = worker(
                queue, mock(PipelineBuildExecutor.class), writer, properties()
        );

        worker.heartbeat(buildId);
        verify(writer, timeout(2_000).times(1)).cancel(buildId);

        worker.heartbeat(buildId);
        verify(writer, timeout(2_000).times(2)).cancel(buildId);
        worker.shutdown();
    }

    private PipelineBuildWorker worker(
            PipelineBuildQueue queue,
            PipelineBuildExecutor executor,
            PipelineDatasetWriter writer,
            PipelineBuildProperties properties
    ) {
        return new PipelineBuildWorker(queue, executor, writer, properties);
    }

    private PipelineBuildProperties properties() {
        PipelineBuildProperties properties = new PipelineBuildProperties();
        properties.setHeartbeatInterval(Duration.ofSeconds(1));
        properties.setLostAfter(Duration.ofSeconds(30));
        properties.setMaxPerPoll(2);
        return properties;
    }
}
