package com.ontoweave.data.sync.execution;

import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;

import java.util.Optional;

public final class SnapshotCompletionDecider {

    private SnapshotCompletionDecider() {
    }

    public static boolean completed(SyncRunEntity run, Optional<IcebergSnapshotInfo> latest) {
        if (run.getRunMode() != SyncMode.CDC || run.getSnapshotCompletedAt() != null) {
            return false;
        }
        Long expectedRows = run.getSnapshotExpectedRows();
        if (expectedRows == null || run.getLastCheckpointAt() == null) {
            return false;
        }
        if (run.getStartedAt() != null && !run.getLastCheckpointAt().isAfter(run.getStartedAt())) {
            return false;
        }
        if (expectedRows == 0) {
            return true;
        }
        return latest.isPresent() && latest.get().rowCount() >= expectedRows;
    }
}
