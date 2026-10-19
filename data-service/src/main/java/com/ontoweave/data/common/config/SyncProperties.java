package com.ontoweave.data.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ontoweave.sync")
public record SyncProperties(
        String flinkRestUrl,
        String flinkHome,
        String flinkCdcHome,
        String icebergCatalog,
        String icebergWarehouse,
        String checkpointDir,
        String savepointDir,
        int parallelism,
        int serverIdBlockSize,
        String commandExchange,
        String commandQueue,
        boolean dispatchEnabled,
        boolean supervisorEnabled,
        int maxDispatchAttempts,
        int defaultVersionIntervalSeconds,
        int checkpointIntervalSeconds,
        int stopSavepointTimeoutSeconds
) {
    public SyncProperties {
        flinkRestUrl = value(flinkRestUrl, "http://localhost:8081");
        flinkHome = value(flinkHome, "/opt/flink");
        flinkCdcHome = value(flinkCdcHome, "/opt/flink-cdc-3.7.0-1.20");
        icebergCatalog = value(icebergCatalog, "ontoweave");
        icebergWarehouse = value(icebergWarehouse, "/data/iceberg/warehouse");
        checkpointDir = value(checkpointDir, "file:///data/flink/checkpoints");
        savepointDir = value(savepointDir, "file:///data/flink/savepoints");
        parallelism = parallelism > 0 ? parallelism : 2;
        serverIdBlockSize = serverIdBlockSize > 0 ? serverIdBlockSize : 100;
        commandExchange = value(commandExchange, "ontoweave.data.commands");
        commandQueue = value(commandQueue, "sync.execute");
        maxDispatchAttempts = maxDispatchAttempts > 0 ? maxDispatchAttempts : 5;
        defaultVersionIntervalSeconds = defaultVersionIntervalSeconds > 0
                ? defaultVersionIntervalSeconds : 86400;
        checkpointIntervalSeconds = checkpointIntervalSeconds > 0 ? checkpointIntervalSeconds : 300;
        stopSavepointTimeoutSeconds = stopSavepointTimeoutSeconds > 0 ? stopSavepointTimeoutSeconds : 30;
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
