package com.ontoweave.data.ontology.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ontoweave.ontology.index")
public record OntologyIndexProperties(
        boolean workerEnabled,
        long pollIntervalMs,
        long initialDelayMs,
        Duration heartbeatInterval,
        Duration leaseDuration,
        Duration recoveryInterval,
        int concurrency,
        int batchSize,
        boolean cleanupEnabled,
        long cleanupInitialDelayMs,
        long cleanupIntervalMs,
        Duration cleanupDelay,
        int cleanupBatchSize,
        int cleanupVersionsPerScan
) {

    public OntologyIndexProperties {
        pollIntervalMs = pollIntervalMs <= 0 ? 1_000L : pollIntervalMs;
        initialDelayMs = Math.max(0L, initialDelayMs);
        heartbeatInterval = positive(heartbeatInterval, Duration.ofSeconds(10));
        leaseDuration = positive(leaseDuration, Duration.ofSeconds(60));
        recoveryInterval = positive(recoveryInterval, Duration.ofSeconds(30));
        concurrency = Math.max(1, concurrency);
        batchSize = Math.max(1, batchSize);
        cleanupInitialDelayMs = Math.max(0L, cleanupInitialDelayMs);
        cleanupIntervalMs = cleanupIntervalMs <= 0 ? 30_000L : cleanupIntervalMs;
        cleanupDelay = cleanupDelay == null ? Duration.ofMinutes(5) : cleanupDelay;
        cleanupBatchSize = cleanupBatchSize <= 0 ? 5_000 : cleanupBatchSize;
        cleanupVersionsPerScan = cleanupVersionsPerScan <= 0 ? 20 : cleanupVersionsPerScan;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
