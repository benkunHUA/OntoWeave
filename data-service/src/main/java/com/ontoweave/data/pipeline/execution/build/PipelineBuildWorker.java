package com.ontoweave.data.pipeline.execution.build;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        prefix = "ontoweave.pipeline.build",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PipelineBuildWorker {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    private static final Duration LOST_AFTER = Duration.ofSeconds(30);
    private static final int MAX_PER_POLL = 10;

    private final PipelineBuildQueue queue;
    private final PipelineBuildExecutor executor;
    private final ScheduledExecutorService heartbeats;
    private final ConcurrentMap<UUID, ScheduledFuture<?>> active = new ConcurrentHashMap<>();

    public PipelineBuildWorker(PipelineBuildQueue queue, PipelineBuildExecutor executor) {
        this.queue = queue;
        this.executor = executor;
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
        queue.recoverLost(LOST_AFTER);
        for (int index = 0; index < MAX_PER_POLL; index++) {
            UUID buildId = queue.claimNext();
            if (buildId == null) {
                return;
            }
            run(buildId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverLostBuilds() {
        queue.recoverLost(LOST_AFTER);
    }

    void run(UUID buildId) {
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(
                () -> heartbeat(buildId),
                HEARTBEAT_INTERVAL.toMillis(),
                HEARTBEAT_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS
        );
        active.put(buildId, heartbeat);
        try {
            executor.execute(buildId);
        } finally {
            ScheduledFuture<?> current = active.remove(buildId);
            if (current != null) {
                current.cancel(false);
            }
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
    void shutdown() {
        heartbeats.shutdownNow();
    }
}
