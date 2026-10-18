package com.ontoweave.data.sync.execution;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotCompletionDeciderTest {

    @Test
    void waitsForCheckpointAfterStart() {
        SyncRunEntity run = run(SyncMode.CDC, 10);
        run.recordCheckpoint(run.getStartedAt(), null);

        assertThat(SnapshotCompletionDecider.completed(run, snapshot(10))).isFalse();
    }

    @Test
    void completesWhenIcebergRowsReachBaseline() {
        SyncRunEntity run = run(SyncMode.CDC, 10);
        run.recordCheckpoint(Instant.now().plusSeconds(1), null);

        assertThat(SnapshotCompletionDecider.completed(run, snapshot(9))).isFalse();
        assertThat(SnapshotCompletionDecider.completed(run, snapshot(10))).isTrue();
    }

    @Test
    void emptySourceCompletesAfterCheckpoint() {
        SyncRunEntity run = run(SyncMode.CDC, 0);
        run.recordCheckpoint(Instant.now().plusSeconds(1), null);

        assertThat(SnapshotCompletionDecider.completed(run, Optional.empty())).isTrue();
    }

    private SyncRunEntity run(SyncMode mode, long expectedRows) {
        SyncRunEntity run = new SyncRunEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                mode,
                "job-" + UUID.randomUUID(),
                "ontoweave",
                "db",
                "tbl",
                86400
        );
        run.markSubmitted("job-id");
        run.recordSnapshotBaseline(expectedRows);
        return run;
    }

    private Optional<IcebergSnapshotInfo> snapshot(long rows) {
        return Optional.of(new IcebergSnapshotInfo(
                1L,
                Instant.now(),
                rows,
                0L,
                JsonNodeFactory.instance.objectNode(),
                1,
                "file:/tmp/table"
        ));
    }
}
