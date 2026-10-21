package com.ontoweave.data.pipeline.source;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.SourceResponse;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineSourceServiceTest extends PipelineSourceServiceTestSupport {

    @Test
    void usesBuildTimeSnapshotAndResolvedVersionAfterTheInputIsRenamed() {
        UUID inputDatasetId = UUID.randomUUID();
        UUID inputVersionId = UUID.randomUUID();
        givenSourceChain(resolvedInputs(resolvedInput(
                "sync",
                inputDatasetId,
                inputVersionId,
                12,
                "hash-1",
                snapshot("orders", 12, "prod-mysql", "sales", "orders")
        )));
        when(metadataQuery.batchLoad(eq(ownerId), anyList())).thenReturn(Map.of(
                new DatasetRef("sync", inputDatasetId),
                new DatasetMetadata(
                        "orders-current",
                        99,
                        UUID.randomUUID(),
                        "renamed-connection",
                        "renamed_schema",
                        "renamed_table",
                        MetadataStatus.AVAILABLE
                )
        ));

        SourceResponse response = service.get(pipelineId, datasetId, sourceVersionId, ownerId);

        assertThat(response.inputs()).singleElement().satisfies(input -> {
            assertThat(input.datasetVersionId()).isEqualTo(inputVersionId);
            assertThat(input.datasetVersionNumber()).isEqualTo(12);
            assertThat(input.datasetName()).isEqualTo("orders");
            assertThat(input.connectionName()).isEqualTo("prod-mysql");
            assertThat(input.sourceSchemaName()).isEqualTo("sales");
            assertThat(input.sourceTableName()).isEqualTo("orders");
            assertThat(input.metadataStatus()).isEqualTo(MetadataStatus.AVAILABLE);
        });
        verify(metadataQuery, times(1)).batchLoad(eq(ownerId), anyList());
    }

    @Test
    void usesCurrentVersionWhenVersionIdIsOmitted() {
        givenSourceChain(resolvedInputs());

        service.get(pipelineId, datasetId, null, ownerId);

        verify(datasetVersions).findByIdAndDatasetId(sourceVersionId, datasetId);
    }

    @Test
    void rejectsUnpublishedVersionAndDatasetOutsideThePipeline() {
        when(datasets.findByIdAndPipelineId(datasetId, pipelineId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(pipelineId, datasetId, sourceVersionId, ownerId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status().value()).isEqualTo(404));

        PipelineDatasetEntity dataset = mock(PipelineDatasetEntity.class);
        when(datasets.findByIdAndPipelineId(datasetId, pipelineId)).thenReturn(Optional.of(dataset));
        PipelineDatasetVersionEntity unpublished = mock(PipelineDatasetVersionEntity.class);
        when(unpublished.getPublishedAt()).thenReturn(null);
        when(datasetVersions.findByIdAndDatasetId(sourceVersionId, datasetId))
                .thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> service.get(pipelineId, datasetId, sourceVersionId, ownerId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status().value()).isEqualTo(404));
    }

    @Test
    void returnsEmptyInputsWhenResolvedInputsAreMissingOrMalformed() {
        givenSourceChain(MAPPER.createArrayNode());
        assertThat(service.get(pipelineId, datasetId, sourceVersionId, ownerId).inputs()).isEmpty();

        ObjectNode malformed = MAPPER.createObjectNode();
        malformed.put("datasetKind", "sync");
        malformed.put("datasetId", "not-a-uuid");
        givenSourceChain(MAPPER.createArrayNode().add(malformed));
        assertThat(service.get(pipelineId, datasetId, sourceVersionId, ownerId).inputs()).isEmpty();
    }

    @Test
    void returnsEmptyInputsWhenAnyResolvedInputElementIsMalformed() {
        UUID validDatasetId = UUID.randomUUID();
        ObjectNode valid = resolvedInput(
                "sync",
                validDatasetId,
                UUID.randomUUID(),
                1,
                "hash-valid",
                snapshot("valid", 1, "conn", "schema", "table")
        );
        ObjectNode malformed = resolvedInput(
                "sync",
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                null,
                null
        );
        givenSourceChain(resolvedInputs(valid, malformed));

        SourceResponse response = service.get(pipelineId, datasetId, sourceVersionId, ownerId);

        assertThat(response.inputs()).isEmpty();
    }
}
