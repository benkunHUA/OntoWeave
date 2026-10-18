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
                VersionSource.SCHEDULED
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
        verify(runs).save(run);
        verify(versions, never()).save(any());
        verify(datasets, never()).save(any());
        verify(outbox, never()).write(any(), any(), any(), any());
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
