package com.ontoweave.data.sync.version;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.VersionSource;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetVersionPublisherTest {

    @Mock
    DatasetRepository datasets;

    @Mock
    DatasetVersionRepository versions;

    @Mock
    SyncRunRepository runs;

    @Mock
    IcebergCatalogClient iceberg;

    @Mock
    OutboxWriter outbox;

    @Test
    void returnsExistingVersionAndRepairsPublishedMarker() {
        UUID datasetId = UUID.randomUUID();
        SyncRunEntity run = run(datasetId);
        run.markSnapshotCompleted(Instant.now().minusSeconds(120));
        run.recordCheckpoint(Instant.now().minusSeconds(60), null);
        DatasetEntity dataset = new DatasetEntity(datasetId, run.getSyncTaskId(), "demo.orders");
        DatasetVersionEntity existing = new DatasetVersionEntity(
                UUID.randomUUID(),
                datasetId,
                1,
                run.getIcebergCatalog(),
                run.getIcebergNamespace(),
                run.getIcebergTable(),
                42L,
                2L,
                true,
                100L,
                JsonNodeFactory.instance.objectNode(),
                1,
                Instant.now(),
                VersionSource.SCHEDULED,
                run.getId(),
                "file:/tmp/table"
        );
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(datasets.findForUpdate(datasetId)).thenReturn(Optional.of(dataset));
        when(iceberg.latestSnapshot(
                run.getIcebergCatalog(),
                run.getIcebergNamespace(),
                run.getIcebergTable()
        )).thenReturn(Optional.of(new IcebergSnapshotInfo(
                42L,
                Instant.now(),
                2L,
                100L,
                JsonNodeFactory.instance.objectNode(),
                1,
                "file:/tmp/table"
        )));
        when(versions.findByDatasetIdAndIcebergTableAndIcebergSnapshotId(
                datasetId,
                run.getIcebergTable(),
                42L
        )).thenReturn(Optional.of(existing));

        Optional<DatasetVersionEntity> published = publisher().publish(
                run.getId(),
                VersionSource.SCHEDULED
        );

        assertThat(published).containsSame(existing);
        assertThat(run.getLastPublishedSnapshotId()).isEqualTo(42L);
        verify(runs).markSnapshotPublished(eq(run.getId()), eq(42L), any(Instant.class), any(Instant.class));
        verify(versions, never()).save(any());
        verify(datasets, never()).save(any());
        verify(outbox, never()).write(any(), any(), any(), any());
    }

    @Test
    void doesNotPublishCdcStopBeforeInitialSnapshotCompletes() {
        UUID datasetId = UUID.randomUUID();
        SyncRunEntity run = run(datasetId);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        Optional<DatasetVersionEntity> published = publisher().publish(run.getId(), VersionSource.STOP);

        assertThat(published).isEmpty();
        verify(iceberg, never()).latestSnapshot(any(), any(), any());
        verify(versions, never()).save(any());
    }

    @Test
    void doesNotPublishCdcStopWithoutCheckpointAfterSnapshot() {
        UUID datasetId = UUID.randomUUID();
        SyncRunEntity run = run(datasetId);
        run.markSnapshotCompleted(Instant.now().minusSeconds(60));
        run.recordCheckpoint(Instant.now().minusSeconds(120), null);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        Optional<DatasetVersionEntity> published = publisher().publish(run.getId(), VersionSource.STOP);

        assertThat(published).isEmpty();
        verify(iceberg, never()).latestSnapshot(any(), any(), any());
    }

    @Test
    void doesNotPublishCdcSnapshotEndWithoutCheckpointAfterSnapshot() {
        UUID datasetId = UUID.randomUUID();
        SyncRunEntity run = run(datasetId);
        run.markSnapshotCompleted(Instant.now().minusSeconds(60));
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        Optional<DatasetVersionEntity> published = publisher().publish(
                run.getId(),
                VersionSource.SNAPSHOT_END
        );

        assertThat(published).isEmpty();
        verify(iceberg, never()).latestSnapshot(any(), any(), any());
    }

    @Test
    void publishesCdcStopWithCheckpointAfterSnapshot() {
        UUID datasetId = UUID.randomUUID();
        SyncRunEntity run = run(datasetId);
        run.markSnapshotCompleted(Instant.now().minusSeconds(60));
        run.recordCheckpoint(Instant.now().minusSeconds(30), null);
        DatasetEntity dataset = new DatasetEntity(datasetId, run.getSyncTaskId(), "demo.orders");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(datasets.findForUpdate(datasetId)).thenReturn(Optional.of(dataset));
        when(iceberg.latestSnapshot(any(), any(), any())).thenReturn(Optional.of(snapshot()));
        when(versions.findTopByDatasetIdOrderByVersionNumberDesc(datasetId)).thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<DatasetVersionEntity> published = publisher().publish(run.getId(), VersionSource.STOP);

        assertThat(published).isPresent();
        assertThat(published.get().getVersionSource()).isEqualTo(VersionSource.STOP);
        verify(iceberg).createTag(any(), any(), any(), eq(42L), any());
        verify(outbox).write(eq("dataset"), eq(datasetId), eq("dataset.version.published"), any());
    }

    private IcebergSnapshotInfo snapshot() {
        return new IcebergSnapshotInfo(
                42L,
                Instant.now(),
                2L,
                100L,
                JsonNodeFactory.instance.objectNode(),
                1,
                "file:/tmp/table"
        );
    }

    private DatasetVersionPublisher publisher() {
        return new DatasetVersionPublisher(datasets, versions, runs, iceberg, outbox);
    }

    private SyncRunEntity run(UUID datasetId) {
        return new SyncRunEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                datasetId,
                UUID.randomUUID(),
                SyncMode.CDC,
                "job-name",
                "ontoweave",
                "namespace",
                "table",
                86400
        );
    }
}
