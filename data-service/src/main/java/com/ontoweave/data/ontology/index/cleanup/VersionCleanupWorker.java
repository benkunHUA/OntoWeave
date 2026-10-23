package com.ontoweave.data.ontology.index.cleanup;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("!test")
@ConditionalOnProperty(
        prefix = "ontoweave.ontology.index",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class VersionCleanupWorker {

    private final VersionCleanupService cleanup;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean shutdown;

    public VersionCleanupWorker(VersionCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(
            fixedDelayString = "${ontoweave.ontology.index.cleanup-interval-ms:30000}",
            initialDelayString = "${ontoweave.ontology.index.cleanup-initial-delay-ms:30000}"
    )
    public void scan() {
        if (shutdown || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanup.runOnce(Instant.now());
        } finally {
            running.set(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown = true;
    }
}
