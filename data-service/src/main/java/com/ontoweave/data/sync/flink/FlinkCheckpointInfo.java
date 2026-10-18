package com.ontoweave.data.sync.flink;

import java.time.Instant;

public record FlinkCheckpointInfo(long checkpointId, Instant completedAt, String externalPath) {
}
