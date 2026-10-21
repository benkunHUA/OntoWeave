package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutputSchemaBaselineTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineDatasetVersionRepository versions =
            mock(PipelineDatasetVersionRepository.class);
    private final OutputSchemaBaseline baseline = new OutputSchemaBaseline(versions);

    @Test
    void prefersTheCurrentPublishedDatasetVersionSchema() {
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        PipelineDatasetEntity dataset = dataset(datasetId, versionId);
        ObjectNode published = schema(field("id", "long"));
        when(versions.findByIdAndDatasetId(versionId, datasetId))
                .thenReturn(Optional.of(version(datasetId, published)));

        assertThat(baseline.resolve(dataset)).isEqualTo(published);
    }

    @Test
    void returnsNoBaselineBeforeTheFirstPublishedVersion() {
        assertThat(baseline.resolve(dataset(UUID.randomUUID(), null))).isNull();
    }

    @Test
    void failedPhysicalColumnsAreNotPartOfTheCompatibilityBaseline() {
        ObjectNode published = schema(field("id", "long"), field("name", "string"));
        ObjectNode pollutedTable = schema(
                field("id", "long"), field("name", "string"), field("failed_extra", "string")
        );
        ObjectNode nextPlan = schema(field("id", "long"), field("name", "string"));

        assertThat(pollutedTable.path("fields")).hasSize(3);
        assertThatCode(() -> OutputSchemaCompatibility.assertCompatible(published, nextPlan))
                .doesNotThrowAnyException();
    }

    private PipelineDatasetEntity dataset(UUID datasetId, UUID versionId) {
        PipelineDatasetEntity dataset = new PipelineDatasetEntity(
                datasetId, UUID.randomUUID(), UUID.randomUUID(), "orders",
                mapper.createArrayNode(), mapper.createArrayNode()
        );
        if (versionId != null) {
            dataset.activateVersion(versionId);
        }
        return dataset;
    }

    private PipelineDatasetVersionEntity version(UUID datasetId, ObjectNode schema) {
        return new PipelineDatasetVersionEntity(
                UUID.randomUUID(), datasetId, UUID.randomUUID(), 1,
                "ontoweave", "pipeline_outputs", "orders", 1L, schema,
                1L, 1L, Instant.now()
        );
    }

    private ObjectNode schema(ObjectNode... fields) {
        ObjectNode schema = mapper.createObjectNode();
        ArrayNode array = schema.putArray("fields");
        for (ObjectNode field : fields) {
            array.add(field);
        }
        return schema;
    }

    private ObjectNode field(String name, String type) {
        return mapper.createObjectNode().put("name", name).put("type", type).put("required", true);
    }
}
