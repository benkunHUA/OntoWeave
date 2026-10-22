package com.ontoweave.data.pipeline.source;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.service.PipelineWriteGuard;
import com.ontoweave.data.sync.api.dto.DatasetPreviewResponse;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import com.ontoweave.data.sync.iceberg.PreviewColumn;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineDatasetPreviewServiceTest {

    private final PipelineWriteGuard pipelines = mock(PipelineWriteGuard.class);
    private final PipelineDatasetRepository datasets = mock(PipelineDatasetRepository.class);
    private final PipelineDatasetVersionRepository versions = mock(PipelineDatasetVersionRepository.class);
    private final IcebergPreviewReader reader = mock(IcebergPreviewReader.class);

    private final PipelineDatasetPreviewService service = new PipelineDatasetPreviewService(
            pipelines, datasets, versions, reader
    );

    @Test
    void readsRowsFromThePublishedImmutableVersionSnapshot() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        PipelineEntity pipeline = new PipelineEntity(
                pipelineId, ownerId, UUID.randomUUID(), "订单清洗"
        );
        PipelineDatasetEntity dataset = dataset(datasetId, pipelineId);
        dataset.activateVersion(versionId);
        PipelineDatasetVersionEntity version = version(datasetId, versionId);
        version.markPublished(Instant.now());
        when(pipelines.requireReadable(pipelineId, ownerId)).thenReturn(pipeline);
        when(datasets.findByIdAndPipelineId(datasetId, pipelineId)).thenReturn(Optional.of(dataset));
        when(versions.findByIdAndDatasetId(versionId, datasetId)).thenReturn(Optional.of(version));
        when(reader.read("ontoweave", "pipeline", "pipeline_table", 901L, 100))
                .thenReturn(Optional.of(new IcebergPreview(
                        List.of(new PreviewColumn("id", "long", false)),
                        List.of(Map.of("id", 1L)),
                        false
                )));

        DatasetPreviewResponse response = service.preview(
                pipelineId, datasetId, versionId, ownerId, 100
        );

        assertThat(response.datasetId()).isEqualTo(datasetId);
        assertThat(response.versionId()).isEqualTo(versionId);
        assertThat(response.versionNumber()).isEqualTo(3);
        assertThat(response.snapshotId()).isEqualTo(901L);
        assertThat(response.live()).isFalse();
        assertThat(response.rowCount()).isEqualTo(1838L);
        assertThat(response.rows()).containsExactly(Map.of("id", 1L));
        assertThat(response.columns()).singleElement().satisfies(column -> {
            assertThat(column.name()).isEqualTo("id");
            assertThat(column.type()).isEqualTo("long");
        });
    }

    @Test
    void rejectsVersionsThatWereNotPublished() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(pipelines.requireReadable(pipelineId, ownerId)).thenReturn(
                new PipelineEntity(pipelineId, ownerId, UUID.randomUUID(), "订单清洗")
        );
        when(datasets.findByIdAndPipelineId(datasetId, pipelineId))
                .thenReturn(Optional.of(dataset(datasetId, pipelineId)));
        when(versions.findByIdAndDatasetId(versionId, datasetId))
                .thenReturn(Optional.of(version(datasetId, versionId)));

        assertThatThrownBy(() -> service.preview(pipelineId, datasetId, versionId, ownerId, 100))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status().value()).isEqualTo(404));
    }

    @Test
    void rejectsInvalidLimitBeforeLoadingTheDataset() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();

        assertThatThrownBy(() -> service.preview(pipelineId, datasetId, null, ownerId, 0))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status().value()).isEqualTo(400));
    }

    private PipelineDatasetEntity dataset(UUID datasetId, UUID pipelineId) {
        return new PipelineDatasetEntity(
                datasetId,
                UUID.randomUUID(),
                pipelineId,
                "orders_customer_detail",
                JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode()
        );
    }

    private PipelineDatasetVersionEntity version(UUID datasetId, UUID versionId) {
        return new PipelineDatasetVersionEntity(
                versionId,
                datasetId,
                UUID.randomUUID(),
                3,
                "ontoweave",
                "pipeline",
                "pipeline_table",
                901L,
                JsonNodeFactory.instance.objectNode(),
                1838L,
                1024L,
                Instant.now()
        );
    }
}
