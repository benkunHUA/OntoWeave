package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UiSchemasTest {

    private static final List<String> CONTROLS = List.of(
            "text", "number", "boolean", "select", "multi-select", "field-selector",
            "operator-selector", "condition-tree", "rename-list", "join-keys",
            "aggregation-list", "expression-tree"
    );

    @Test
    void mapsEveryConfigFieldToAnAllowedControl() {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        assertThat(UiSchemas.controls()).containsExactlyElementsOf(CONTROLS);
        Map<String, Map<String, String>> expected = Map.of(
                "input", Map.of("datasetKind", "select", "datasetId", "select"),
                "filter", Map.of("condition", "condition-tree"),
                "select_columns", Map.of("mode", "select", "columns", "multi-select"),
                "rename_columns", Map.of("renames", "rename-list"),
                "derive_column", Map.of("outputField", "text", "expression", "expression-tree"),
                "deduplicate", Map.of("mode", "select", "columns", "multi-select"),
                "join", Map.of("joinType", "select", "keys", "join-keys"),
                "union", Map.of(),
                "aggregate", Map.of("groupBy", "field-selector", "aggregations", "aggregation-list"),
                "output", Map.of("outputId", "select")
        );

        for (NodeDefinition node : catalog.nodes()) {
            JsonNode fields = node.uiSchema().path("fields");
            Map<String, String> expectedFields = expected.get(node.type());
            assertThat(fields.isObject()).as(node.type()).isTrue();
            assertThat(fieldNames(fields)).as(node.type())
                    .containsExactlyInAnyOrderElementsOf(expectedFields.keySet());
            expectedFields.forEach((field, control) -> {
                assertThat(fields.path(field).path("widget").asText())
                        .as(node.type() + "." + field)
                        .isEqualTo(control);
                assertThat(CONTROLS).contains(control);
            });
        }
        assertThat(fieldNames(catalog.require("union").uiSchema().path("fields"))).isEmpty();
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
