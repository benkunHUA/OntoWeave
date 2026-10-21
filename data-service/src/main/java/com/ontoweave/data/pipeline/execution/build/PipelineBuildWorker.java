package com.ontoweave.data.pipeline.execution.build;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "ontoweave.pipeline.build",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PipelineBuildWorker {

    private final PipelineBuildQueue queue;
    private final PipelineBuildExecutor buildExecutor;
    private final PipelineBuildProperties properties;
    private final Semaphore slots;
    private final ThreadPoolExecutor workers;
    private final java.util.concurrent.ExecutorService recovery;
    private final ScheduledExecutorService heartbeats;
    private final ConcurrentMap<UUID, ScheduledFuture<?>> active = new ConcurrentHashMap<>();
    private final AtomicBoolean recovering = new AtomicBoolean();
    private volatile boolean shutdown;

    public PipelineBuildWorker(
            PipelineBuildQueue queue,
            PipelineBuildExecutor buildExecutor,
            PipelineBuildProperties properties
    ) {
        this.queue = queue;
        this.buildExecutor = buildExecutor;
        this.properties = properties;
        int threads = Math.max(1, properties.getExecutorThreads());
        int capacity = Math.max(0, properties.getExecutorQueueCapacity());
        this.slots = new Semaphore(threads + capacity);
        this.workers = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                capacity == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(capacity),
                new WorkerThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.recovery = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pipeline-build-recovery");
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeats = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pipeline-build-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Scheduled(
            fixedDelayString = "${ontoweave.pipeline.build.poll-interval-ms:2000}",
            initialDelayString = "${ontoweave.pipeline.build.initial-delay-ms:3000}"
    )
    public void poll() {
        if (shutdown) {
            return;
        }
        scheduleRecovery();
        for (int index = 0; index < properties.getMaxPerPoll(); index++) {
            if (!slots.tryAcquire()) {
                return;
            }
            UUID buildId = queue.claimNext();
            if (buildId == null) {
                slots.release();
                return;
            }
            if (!submit(buildId)) {
                slots.release();
                queue.requeue(buildId);
                return;
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverLostBuilds() {
        scheduleRecovery();
    }

    private void scheduleRecovery() {
        if (shutdown || !recovering.compareAndSet(false, true)) {
            return;
        }
        try {
            recovery.execute(() -> {
                try {
                    queue.recoverLost(properties.getLostAfter());
                } catch (RuntimeException ignored) {
                    // Recovery is retried on the next poll; it must not stop build scheduling.
                } finally {
                    recovering.set(false);
                }
            });
        } catch (RejectedExecutionException error) {
            recovering.set(false);
        }
    }

    boolean submit(UUID buildId) {
        if (shutdown) {
            return false;
        }
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(
                () -> heartbeat(buildId),
                properties.getHeartbeatInterval().toMillis(),
                properties.getHeartbeatInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
        active.put(buildId, heartbeat);
        try {
            workers.execute(() -> {
                try {
                    buildExecutor.execute(buildId);
                } finally {
                    finish(buildId);
                    slots.release();
                }
            });
            return true;
        } catch (RejectedExecutionException error) {
            finish(buildId);
            return false;
        }
    }

    private void finish(UUID buildId) {
        ScheduledFuture<?> current = active.remove(buildId);
        if (current != null) {
            current.cancel(false);
        }
    }

    private void heartbeat(UUID buildId) {
        try {
            queue.heartbeat(buildId);
        } catch (RuntimeException ignored) {
            // A failed heartbeat is retried by recovery; it must not stop the build thread.
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown = true;
        recovery.shutdownNow();
        workers.shutdownNow();
        heartbeats.shutdownNow();
        active.keySet().forEach(this::finish);
    }

    private static final class WorkerThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "pipeline-build-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
