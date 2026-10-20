package com.ontoweave.data.sync.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.sync.api.dto.DatasetPreviewResponse;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;
import com.ontoweave.data.sync.iceberg.PreviewColumn;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetPreviewServiceTest {

    @Mock
    DatasetService datasets;

    @Mock
    DatasetVersionRepository versions;

    @Mock
    SyncRunRepository runs;

    @Mock
    IcebergCatalogClient iceberg;

    @Mock
    IcebergPreviewReader reader;

    @Test
    void livePreviewUsesLatestSnapshotWithoutVersion() {
        UUID ownerId = UUID.randomUUID();
        DatasetEntity dataset = dataset();
        SyncRunEntity run = run(dataset.getId(), "tbl-new");
        when(datasets.required(dataset.getId(), ownerId)).thenReturn(dataset);
        when(runs.findByDatasetIdOrderByCreatedAtDesc(dataset.getId())).thenReturn(List.of(run));
        when(iceberg.latestSnapshot("ontoweave", "ns", "tbl-new")).thenReturn(Optional.of(snapshot(9L)));
        when(reader.read("ontoweave", "ns", "tbl-new", 9L, 100)).thenReturn(Optional.of(preview()));

        DatasetPreviewResponse result = service().preview(
                dataset.getId(), null, ownerId, 100, true
        );

        assertThat(result.live()).isTrue();
        assertThat(result.versionId()).isNull();
        assertThat(result.versionNumber()).isNull();
        assertThat(result.snapshotId()).isEqualTo(9L);
        assertThat(result.rows()).containsExactly(Map.of("id", 1L));
    }

    @Test
    void livePreviewFallsBackWhenNewestRunHasNoSnapshot() {
        UUID ownerId = UUID.randomUUID();
        DatasetEntity dataset = dataset();
        SyncRunEntity newest = run(dataset.getId(), "tbl-new");
        SyncRunEntity previous = run(dataset.getId(), "tbl-old");
        when(datasets.required(dataset.getId(), ownerId)).thenReturn(dataset);
        when(runs.findByDatasetIdOrderByCreatedAtDesc(dataset.getId()))
                .thenReturn(List.of(newest, previous));
        when(iceberg.latestSnapshot("ontoweave", "ns", "tbl-new"))
                .thenReturn(Optional.empty());
        when(iceberg.latestSnapshot("ontoweave", "ns", "tbl-old"))
                .thenReturn(Optional.of(snapshot(7L)));
        when(reader.read("ontoweave", "ns", "tbl-old", 7L, 100))
                .thenReturn(Optional.of(preview()));

        DatasetPreviewResponse result = service().preview(
                dataset.getId(), null, ownerId, 100, true
        );

        assertThat(result.snapshotId()).isEqualTo(7L);
    }

    private DatasetPreviewService service() {
        return new DatasetPreviewService(datasets, versions, runs, iceberg, reader);
    }

    private DatasetEntity dataset() {
        return new DatasetEntity(UUID.randomUUID(), UUID.randomUUID(), "demo.customers");
    }

    private SyncRunEntity run(UUID datasetId, String table) {
        return new SyncRunEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                datasetId,
                UUID.randomUUID(),
                SyncMode.CDC,
                "job-" + UUID.randomUUID(),
                "ontoweave",
                "ns",
                table,
                86400
        );
    }

    private IcebergSnapshotInfo snapshot(long snapshotId) {
        return new IcebergSnapshotInfo(
                snapshotId,
                Instant.now(),
                1L,
                10L,
                JsonNodeFactory.instance.objectNode(),
                1,
                "file:/tmp/table"
        );
    }

    private IcebergPreview preview() {
        return new IcebergPreview(
                List.of(new PreviewColumn("id", "long", false)),
                List.of(Map.of("id", 1L)),
                false
        );
    }
}
