package com.ontoweave.data.pipeline.execution.build;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        PipelineBuildWorker worker = new PipelineBuildWorker(queue, executor, properties());

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
        PipelineBuildWorker worker = new PipelineBuildWorker(queue, executor, properties);

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
        PipelineBuildWorker worker = new PipelineBuildWorker(queue, executor, properties());

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
        PipelineBuildWorker worker = new PipelineBuildWorker(queue, executor, properties());

        worker.shutdown();
        worker.poll();

        verify(queue, never()).recoverLost(org.mockito.ArgumentMatchers.any());
        verify(queue, never()).claimNext();
    }

    private PipelineBuildProperties properties() {
        PipelineBuildProperties properties = new PipelineBuildProperties();
        properties.setHeartbeatInterval(Duration.ofSeconds(1));
        properties.setLostAfter(Duration.ofSeconds(30));
        properties.setMaxPerPoll(2);
        return properties;
    }
}
