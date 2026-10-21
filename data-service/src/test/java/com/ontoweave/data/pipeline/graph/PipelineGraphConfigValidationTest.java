package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineGraphConfigValidationTest extends PipelineGraphValidatorTestSupport {

    @Test
    void rejectsReservedDerivedAndRenameTargetNames() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode deriveConfig = mapper.createObjectNode();
        deriveConfig.put("outputField", "__ontoweave_preview_user_field");
        deriveConfig.set("expression", mapper.createObjectNode()
                .put("kind", "field").put("field", "id"));
        ObjectNode deriveDraft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(FILTER, "derive_column", deriveConfig)),
                mapper.createArrayNode().add(edge(INPUT_A, "out", FILTER, "in"))
        );

        assertThat(codes(validate(deriveDraft, inputSchema(datasetId))))
                .contains("INVALID_FIELD_NAME");
    }

    @Test
    void rejectsEmptySelectJoinAndAggregateConfigurationInGraphValidation() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode select = mapper.createObjectNode().put("mode", "include");
        select.putArray("columns");
        ObjectNode join = mapper.createObjectNode().put("joinType", "left");
        join.putArray("keys");
        ObjectNode aggregate = mapper.createObjectNode();
        aggregate.putArray("groupBy").add("id");
        aggregate.putArray("aggregations");

        for (ObjectNode config : List.of(select, join, aggregate)) {
            String type = config.has("columns") ? "select_columns"
                    : config.has("keys") ? "join" : "aggregate";
            ObjectNode draft = draft(
                    mapper.createArrayNode()
                            .add(node(INPUT_A, "input", inputConfig(datasetId)))
                            .add(node(FILTER, type, config)),
                    mapper.createArrayNode().add(edge(INPUT_A, "out", FILTER, "in"))
            );
            assertThat(codes(validate(draft, inputSchema(datasetId))))
                    .as(type)
                    .contains("CONFIG_INVALID");
        }
    }

    private Map<UUID, InputSchema> inputSchema(UUID datasetId) {
        return Map.of(datasetId, new InputSchema(
                UUID.randomUUID(), List.of(new FieldSchema("id", "long", false))
        ));
    }
}
