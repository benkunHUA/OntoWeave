package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineGraphPublishedInputSchemaResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesAllInputsWithBatchQueries() {
        UUID ownerId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID versionA = UUID.randomUUID();
        UUID versionB = UUID.randomUUID();
        DatasetEntity datasetA = dataset(UUID.randomUUID(), connectionId, versionA);
        DatasetEntity datasetB = dataset(UUID.randomUUID(), connectionId, versionB);

        DatasetRepository datasets = mock(DatasetRepository.class);
        DatasetVersionRepository versions = mock(DatasetVersionRepository.class);
        DataConnectionRepository connections = mock(DataConnectionRepository.class);
        when(datasets.findAllById(Set.of(datasetA.getId(), datasetB.getId())))
                .thenReturn(List.of(datasetA, datasetB));
        DataConnectionEntity connection = mock(DataConnectionEntity.class);
        when(connection.getId()).thenReturn(connectionId);
        when(connections.findByIdInAndOwnerId(Set.of(connectionId), ownerId))
                .thenReturn(List.of(connection));
        DatasetVersionEntity first = version(versionA, datasetA.getId());
        DatasetVersionEntity second = version(versionB, datasetB.getId());
        when(versions.findAllById(Set.of(versionA, versionB))).thenReturn(List.of(first, second));

        Map<UUID, InputSchema> resolved = new PublishedInputSchemaResolver(datasets, versions, connections)
                .resolveAll(ownerId, Set.of(datasetA.getId(), datasetB.getId()));

        assertThat(resolved).containsOnlyKeys(datasetA.getId(), datasetB.getId());
        assertThat(resolved.get(datasetA.getId()).fields())
                .extracting(FieldSchema::name)
                .containsExactly("id");
        verify(datasets).findAllById(any());
        verify(datasets, never()).findById(any());
        verify(connections).findByIdInAndOwnerId(any(), any());
        verify(versions).findAllById(any());
    }

    private DatasetEntity dataset(UUID id, UUID connectionId, UUID versionId) {
        DatasetEntity dataset = mock(DatasetEntity.class);
        when(dataset.getId()).thenReturn(id);
        when(dataset.getConnectionId()).thenReturn(connectionId);
        when(dataset.getCurrentVersionId()).thenReturn(versionId);
        return dataset;
    }

    private DatasetVersionEntity version(UUID id, UUID datasetId) {
        DatasetVersionEntity version = mock(DatasetVersionEntity.class);
        when(version.getId()).thenReturn(id);
        when(version.getDatasetId()).thenReturn(datasetId);
        when(version.getPublishedAt()).thenReturn(Instant.now());
        when(version.getSchemaJson()).thenReturn(mapper.createObjectNode()
                .set("fields", mapper.createArrayNode().add(mapper.createObjectNode()
                        .put("name", "id")
                        .put("type", "long")
                        .put("required", true))));
        return version;
    }
}
