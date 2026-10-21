package com.ontoweave.data.pipeline.source;

import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineSourceMetadataQueryTest {

    @Mock
    DatasetRepository datasets;

    @Mock
    DatasetVersionRepository datasetVersions;

    @Mock
    SyncTaskRepository syncTasks;

    @Mock
    DataConnectionRepository connections;

    @Mock
    PipelineRepository pipelines;

    @Mock
    PipelineDatasetRepository pipelineDatasets;

    @Mock
    PipelineDatasetVersionRepository pipelineDatasetVersions;

    private DatasetMetadataQuery query;

    @BeforeEach
    void setUp() {
        query = new DatasetMetadataQuery(
                datasets,
                datasetVersions,
                syncTasks,
                connections,
                pipelines,
                pipelineDatasets,
                pipelineDatasetVersions
        );
    }

    @Test
    void restrictsPipelineInputWhenOwningPipelineIsNotVisibleToOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        PipelineDatasetEntity dataset = mock(PipelineDatasetEntity.class);
        when(dataset.getId()).thenReturn(datasetId);
        when(dataset.getPipelineId()).thenReturn(pipelineId);
        when(pipelineDatasets.findByIdIn(List.of(datasetId))).thenReturn(List.of(dataset));
        when(pipelines.findByIdInAndOwnerId(anyCollection(), eq(ownerId))).thenReturn(List.of());

        Map<DatasetRef, DatasetMetadata> result = query.batchLoad(
                ownerId,
                List.of(new DatasetRef("pipeline", datasetId))
        );

        assertThat(result.get(new DatasetRef("pipeline", datasetId)).status())
                .isEqualTo(MetadataStatus.RESTRICTED);
        verify(pipelines, times(1)).findByIdInAndOwnerId(anyCollection(), eq(ownerId));
    }
}
