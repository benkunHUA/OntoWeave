package com.ontoweave.data.pipeline.source;

import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
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

    @Test
    void loadsAllSyncInputsWithOneQueryPerRepository() {
        UUID ownerId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID firstVersionId = UUID.randomUUID();
        UUID secondVersionId = UUID.randomUUID();
        DatasetEntity first = dataset(firstId, connectionId, "first", firstVersionId);
        DatasetEntity second = dataset(secondId, connectionId, "second", secondVersionId);
        DatasetVersionEntity firstVersion = version(firstVersionId, 1);
        DatasetVersionEntity secondVersion = version(secondVersionId, 2);
        SyncTaskEntity firstTask = task(firstId, "schema", "first_table");
        SyncTaskEntity secondTask = task(secondId, "schema", "second_table");
        DataConnectionEntity connection = connection(connectionId);
        when(datasets.findByIdIn(anyCollection())).thenReturn(List.of(first, second));
        when(datasetVersions.findByIdIn(anyCollection())).thenReturn(List.of(firstVersion, secondVersion));
        when(syncTasks.findByDatasetIdIn(anyCollection())).thenReturn(List.of(firstTask, secondTask));
        when(connections.findByIdInAndOwnerId(anyCollection(), eq(ownerId)))
                .thenReturn(List.of(connection));

        Map<DatasetRef, DatasetMetadata> result = query.batchLoad(ownerId, List.of(
                new DatasetRef("sync", firstId),
                new DatasetRef("sync", secondId)
        ));

        assertThat(result.values()).allMatch(metadata -> metadata.status() == MetadataStatus.AVAILABLE);
        verify(datasets, times(1)).findByIdIn(anyCollection());
        verify(datasetVersions, times(1)).findByIdIn(anyCollection());
        verify(syncTasks, times(1)).findByDatasetIdIn(anyCollection());
        verify(connections, times(1)).findByIdInAndOwnerId(anyCollection(), eq(ownerId));
    }

    private DatasetEntity dataset(UUID id, UUID connectionId, String name, UUID versionId) {
        DatasetEntity dataset = mock(DatasetEntity.class);
        when(dataset.getId()).thenReturn(id);
        when(dataset.getConnectionId()).thenReturn(connectionId);
        when(dataset.getName()).thenReturn(name);
        when(dataset.getCurrentVersionId()).thenReturn(versionId);
        return dataset;
    }

    private DatasetVersionEntity version(UUID id, int versionNumber) {
        DatasetVersionEntity version = mock(DatasetVersionEntity.class);
        when(version.getId()).thenReturn(id);
        when(version.getVersionNumber()).thenReturn(versionNumber);
        return version;
    }

    private SyncTaskEntity task(UUID datasetId, String schemaName, String tableName) {
        SyncTaskEntity task = mock(SyncTaskEntity.class);
        when(task.getDatasetId()).thenReturn(datasetId);
        when(task.getSchemaName()).thenReturn(schemaName);
        when(task.getTableName()).thenReturn(tableName);
        return task;
    }

    private DataConnectionEntity connection(UUID id) {
        DataConnectionEntity connection = mock(DataConnectionEntity.class);
        when(connection.getId()).thenReturn(id);
        when(connection.getName()).thenReturn("connection");
        return connection;
    }
}
