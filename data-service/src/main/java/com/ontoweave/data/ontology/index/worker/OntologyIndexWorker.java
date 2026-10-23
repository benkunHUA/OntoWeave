package com.ontoweave.data.ontology.index.worker;

import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.index.OntologyIndexProperties;
import com.ontoweave.data.ontology.index.run.OntologyRunStateStore;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
@ConditionalOnProperty(
        prefix = "ontoweave.ontology.index",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OntologyIndexWorker {

    private final OntologyRunStateStore stateStore;
    private final OntologyIndexExecutor executor;
    private final OntologyIndexProperties properties;
    private final Semaphore slots;
    private final ExecutorService workers;
    private final ScheduledExecutorService heartbeats;
    private final ConcurrentMap<UUID, ScheduledFuture<?>> active = new ConcurrentHashMap<>();
    private final String workerId = "ontology-index-" + UUID.randomUUID();
    private volatile boolean shutdown;

    public OntologyIndexWorker(
            OntologyRunStateStore stateStore,
            OntologyIndexExecutor executor,
            OntologyIndexProperties properties
    ) {
        this.stateStore = stateStore;
        this.executor = executor;
        this.properties = properties;
        this.slots = new Semaphore(properties.concurrency());
        this.workers = Executors.newFixedThreadPool(properties.concurrency(), runnable -> {
            Thread thread = new Thread(runnable, "ontology-index-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeats = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ontology-index-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Scheduled(
            fixedDelayString = "${ontoweave.ontology.index.poll-interval-ms:1000}",
            initialDelayString = "${ontoweave.ontology.index.initial-delay-ms:1000}"
    )
    public void poll() {
        if (shutdown) {
            return;
        }
        while (slots.tryAcquire()) {
            Optional<OntologyIndexRunEntity> claimed = stateStore.claimNext(workerId);
            if (claimed.isEmpty()) {
                slots.release();
                return;
            }
            if (!submit(claimed.get())) {
                slots.release();
                return;
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverLost();
    }

    @Scheduled(
            fixedDelayString = "${ontoweave.ontology.index.recovery-interval:30s}",
            initialDelayString = "30000"
    )
    public void recoverLost() {
        if (shutdown) {
            return;
        }
        stateStore.recoverExpired(100, Instant.now());
    }

    private boolean submit(OntologyIndexRunEntity run) {
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(
                () -> stateStore.renewLease(run.getId(), workerId),
                properties.heartbeatInterval().toMillis(),
                properties.heartbeatInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
        active.put(run.getId(), heartbeat);
        try {
            workers.execute(() -> {
                try {
                    executor.execute(run, workerId);
                } finally {
                    finish(run.getId());
                    slots.release();
                }
            });
            return true;
        } catch (RuntimeException exception) {
            finish(run.getId());
            return false;
        }
    }

    private void finish(UUID runId) {
        ScheduledFuture<?> heartbeat = active.remove(runId);
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown = true;
        workers.shutdownNow();
        heartbeats.shutdownNow();
        active.keySet().forEach(this::finish);
    }
}
