package com.ontoweave.data.pipeline.source;

import com.ontoweave.data.pipeline.api.dto.SourceInput;
import com.ontoweave.data.pipeline.api.dto.SourceResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineSourceMetadataTest extends PipelineSourceServiceTestSupport {

    @Test
    void mapsAvailableRestrictedAndMissingMetadataAndLoadsThemInOneBatch() {
        UUID availableId = UUID.randomUUID();
        UUID restrictedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        givenSourceChain(resolvedInputs(
                resolvedInput("sync", availableId, UUID.randomUUID(), 1, "hash-a", null),
                resolvedInput("sync", restrictedId, UUID.randomUUID(), 2, "hash-r", null),
                resolvedInput("sync", missingId, UUID.randomUUID(), 3, "hash-m",
                        snapshot("saved-name", 3, "saved-conn", "saved_schema", "saved_table"))
        ));
        Map<DatasetRef, DatasetMetadata> metadata = new LinkedHashMap<>();
        metadata.put(new DatasetRef("sync", availableId), new DatasetMetadata(
                "available-name", 1, UUID.randomUUID(), "available-conn", "available_schema",
                "available_table", MetadataStatus.AVAILABLE
        ));
        metadata.put(new DatasetRef("sync", restrictedId), DatasetMetadata.restricted());
        when(metadataQuery.batchLoad(eq(ownerId), anyList())).thenReturn(metadata);

        SourceResponse response = service.get(pipelineId, datasetId, sourceVersionId, ownerId);

        assertThat(response.inputs()).hasSize(3);
        assertThat(response.inputs().get(0).datasetName()).isEqualTo("available-name");
        assertThat(response.inputs().get(0).connectionName()).isEqualTo("available-conn");
        assertThat(response.inputs().get(0).metadataStatus()).isEqualTo(MetadataStatus.AVAILABLE);

        SourceInput restricted = response.inputs().get(1);
        assertThat(restricted.datasetName()).isNull();
        assertThat(restricted.connectionName()).isNull();
        assertThat(restricted.sourceSchemaName()).isNull();
        assertThat(restricted.sourceTableName()).isNull();
        assertThat(restricted.metadataStatus()).isEqualTo(MetadataStatus.RESTRICTED);

        SourceInput missing = response.inputs().get(2);
        assertThat(missing.datasetName()).isEqualTo("saved-name");
        assertThat(missing.connectionName()).isEqualTo("saved-conn");
        assertThat(missing.sourceSchemaName()).isEqualTo("saved_schema");
        assertThat(missing.sourceTableName()).isEqualTo("saved_table");
        assertThat(missing.metadataStatus()).isEqualTo(MetadataStatus.MISSING);

        verify(metadataQuery, times(1)).batchLoad(eq(ownerId), anyList());
    }

    @Test
    void leavesDisplayFieldsNullWhenMissingMetadataHasNoSnapshot() {
        UUID missingId = UUID.randomUUID();
        givenSourceChain(resolvedInputs(
                resolvedInput("sync", missingId, UUID.randomUUID(), 4, "hash-missing", null)
        ));
        when(metadataQuery.batchLoad(eq(ownerId), anyList())).thenReturn(Map.of(
                new DatasetRef("sync", missingId), DatasetMetadata.missing()
        ));

        SourceResponse response = service.get(pipelineId, datasetId, sourceVersionId, ownerId);

        assertThat(response.inputs()).singleElement().satisfies(input -> {
            assertThat(input.metadataStatus()).isEqualTo(MetadataStatus.MISSING);
            assertThat(input.datasetName()).isNull();
            assertThat(input.connectionName()).isNull();
            assertThat(input.sourceSchemaName()).isNull();
            assertThat(input.sourceTableName()).isNull();
        });
    }
}
