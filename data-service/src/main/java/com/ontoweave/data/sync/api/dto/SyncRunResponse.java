package com.ontoweave.data.sync.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SyncRunResponse(
        UUID id,
        UUID syncTaskId,
        UUID datasetId,
        UUID operationId,
        String runMode,
        String status,
        String flinkJobId,
        String flinkJobName,
        String flinkJobState,
        String icebergCatalog,
        String icebergNamespace,
        String icebergTable,
        Long serverIdStart,
        Long serverIdEnd,
        Instant snapshotCompletedAt,
        Instant lastCheckpointAt,
        Long lastSnapshotId,
        Long lastPublishedSnapshotId,
        int versionIntervalSeconds,
        Instant nextVersionDueAt,
        String savepointPath,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
