package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PipelineNodeCatalogTest {

    private static final List<String> TYPES = List.of(
            "input", "filter", "select_columns", "rename_columns", "derive_column",
            "deduplicate", "join", "union", "aggregate", "output"
    );
    private static final List<String> SINGLE_INPUT_TRANSFORMS = List.of(
            "filter", "select_columns", "rename_columns", "derive_column", "deduplicate", "aggregate"
    );
    private static final List<String> FILTER_OPS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte", "is_null", "is_not_null", "in"
    );
    private static final List<String> EXPRESSION_KINDS = List.of(
            "field", "literal", "cast", "case", "compare", "logical", "isNull"
    );
    private static final Pattern CHINESE = Pattern.compile(".*[\\u4e00-\\u9fff].*");

    @Test
    void exposesMvpNodesAndPorts() {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        assertThat(catalog.types()).containsExactlyInAnyOrderElementsOf(TYPES);
        assertThat(catalog.require("join").ports())
                .extracting(NodePort::id)
                .containsExactly("left", "right", "out");
        assertThat(catalog.require("output").ports())
                .extracting(NodePort::id)
                .containsExactly("in");
        assertThat(catalog.require("filter").configSchema().path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(catalog.require("filter").configSchema().path("required").isArray())
                .isFalse();
    }

    @Test
    void declaresVersionLabelsAndCategories() {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        for (NodeDefinition node : catalog.nodes()) {
            assertThat(node.version()).as(node.type()).isEqualTo(1);
            assertThat(node.label()).as(node.type()).isNotBlank().matches(CHINESE);
            assertThat(node.category()).as(node.type()).isEqualTo(categoryOf(node.type()));
            assertThat(node.ports()).as(node.type()).isNotEmpty();
        }
        assertThat(catalog.require("input").category()).isEqualTo("source");
        assertThat(catalog.require("output").category()).isEqualTo("output");
        for (String type : SINGLE_INPUT_TRANSFORMS) {
            assertThat(catalog.require(type).category()).isEqualTo("transform");
        }
        assertThat(catalog.require("join").category()).isEqualTo("transform");
        assertThat(catalog.require("union").category()).isEqualTo("transform");
    }

    @Test
    void declaresFixedPortContracts() {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        assertPort(catalog.require("input"), "out", NodePortRole.OUTPUT, false, false);
        for (String type : SINGLE_INPUT_TRANSFORMS) {
            assertPort(catalog.require(type), "in", NodePortRole.INPUT, true, false);
            assertPort(catalog.require(type), "out", NodePortRole.OUTPUT, false, false);
        }
        assertPort(catalog.require("join"), "left", NodePortRole.INPUT, true, false);
        assertPort(catalog.require("join"), "right", NodePortRole.INPUT, true, false);
        assertPort(catalog.require("join"), "out", NodePortRole.OUTPUT, false, false);
        assertPort(catalog.require("union"), "inputs", NodePortRole.INPUT, true, true);
        assertPort(catalog.require("union"), "out", NodePortRole.OUTPUT, false, false);
        assertPort(catalog.require("output"), "in", NodePortRole.INPUT, true, false);

        for (NodeDefinition node : catalog.nodes()) {
            assertThat(node.ports()).as(node.type()).allSatisfy(port -> {
                assertThat(port.dataType()).as(node.type() + "." + port.id()).isEqualTo("table");
            });
            for (NodePort port : node.ports()) {
                if (port.multiple()) {
                    assertThat(node.type()).isEqualTo("union");
                    assertThat(port.id()).isEqualTo("inputs");
                }
            }
        }
    }

    @Test
    void usesStrictObjectConfigSchemasWithoutTopLevelRequiredFields() {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        for (NodeDefinition node : catalog.nodes()) {
            JsonNode schema = node.configSchema();
            assertThat(schema.path("type").asText()).as(node.type()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean()).as(node.type()).isFalse();
            assertThat(schema.path("required").isArray()).as(node.type()).isFalse();
            assertThat(schema.path("properties").isObject()).as(node.type()).isTrue();
        }
        assertThat(catalog.require("union").configSchema().path("properties").isEmpty()).isTrue();
    }

    @Test
    void declaresFixedConfigPropertiesAndEnums() {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        assertProperties(catalog.require("input"), "datasetKind", "datasetId");
        assertEnum(catalog.require("input"), "datasetKind", "sync");
        assertString(catalog.require("input"), "datasetId");
        assertThat(catalog.require("input").configSchema()
                .path("properties").path("datasetId").path("format").asText()).isEqualTo("uuid");

        assertProperties(catalog.require("filter"), "condition");
        JsonNode condition = catalog.require("filter").configSchema()
                .path("properties").path("condition");
        assertThat(condition.path("type").asText()).isEqualTo("object");
        assertEnumValues(condition.path("properties").path("kind"), "compare", "and", "or");
        assertThat(enumValues(condition.path("properties").path("op"))).containsExactlyElementsOf(FILTER_OPS);
        assertThat(condition.path("properties").path("conditions").path("items").path("$ref").asText())
                .isEqualTo("#/$defs/condition");

        assertProperties(catalog.require("select_columns"), "mode", "columns");
        assertEnum(catalog.require("select_columns"), "mode", "include", "exclude");
        assertStringArray(catalog.require("select_columns"), "columns");

        assertProperties(catalog.require("rename_columns"), "renames");
        assertObjectArrayRequired(catalog.require("rename_columns"), "renames", "from", "to");

        assertProperties(catalog.require("derive_column"), "outputField", "expression");
        assertString(catalog.require("derive_column"), "outputField");
        JsonNode expression = catalog.require("derive_column").configSchema()
                .path("properties").path("expression");
        assertThat(expression.path("type").asText()).isEqualTo("object");
        assertThat(expression.path("additionalProperties").asBoolean()).isFalse();
        assertThat(enumValues(expression.path("properties").path("kind")))
                .containsExactlyElementsOf(EXPRESSION_KINDS);

        assertProperties(catalog.require("deduplicate"), "mode", "columns");
        assertEnum(catalog.require("deduplicate"), "mode", "all", "subset");
        assertStringArray(catalog.require("deduplicate"), "columns");

        assertProperties(catalog.require("join"), "joinType", "keys");
        assertEnum(catalog.require("join"), "joinType", "left", "right", "inner", "full");
        assertObjectArrayRequired(catalog.require("join"), "keys", "left", "right");

        assertProperties(catalog.require("aggregate"), "groupBy", "aggregations");
        assertStringArray(catalog.require("aggregate"), "groupBy");
        JsonNode aggregation = catalog.require("aggregate").configSchema()
                .path("properties").path("aggregations").path("items");
        assertThat(aggregation.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("op", "outputField");
        assertEnumValues(aggregation.path("properties").path("op"), "count", "sum", "min", "max", "avg");
        assertThat(aggregation.path("properties").path("field").path("type").asText()).isEqualTo("string");
        assertThat(aggregation.path("properties").path("outputField").path("type").asText()).isEqualTo("string");

        assertProperties(catalog.require("output"), "outputId");
        assertString(catalog.require("output"), "outputId");
        assertThat(catalog.require("output").configSchema()
                .path("properties").path("outputId").path("format").asText()).isEqualTo("uuid");
    }

    @Test
    void catalogVersionIsCanonicalAndDeterministic() {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        String version = catalog.catalogVersion();
        assertThat(version).matches("[0-9a-f]{64}");
        assertThat(catalog.response().catalogVersion()).isEqualTo(version);
        assertThat(PipelineNodeCatalog.defaultCatalog().catalogVersion()).isEqualTo(version);

        List<NodeDefinition> reversed = new ArrayList<>(catalog.nodes());
        java.util.Collections.reverse(reversed);
        assertThat(new PipelineNodeCatalog(reversed).catalogVersion()).isEqualTo(version);
    }

    @Test
    void requiresKnownNodeType() {
        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> PipelineNodeCatalog.defaultCatalog().require("run")
        );
        assertThat(exception).isNotNull();
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.code()).isEqualTo("NODE_TYPE_UNKNOWN");
    }

    @Test
    void isAvailableAsSpringBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(PipelineNodeCatalog.class);
            context.refresh();

            assertThat(context.getBean(PipelineNodeCatalog.class).types())
                    .containsExactlyInAnyOrderElementsOf(TYPES);
        }
    }

    private static String categoryOf(String type) {
        if ("input".equals(type)) {
            return "source";
        }
        if ("output".equals(type)) {
            return "output";
        }
        return "transform";
    }

    private static void assertPort(
            NodeDefinition node,
            String id,
            NodePortRole role,
            boolean required,
            boolean multiple
    ) {
        NodePort port = node.ports().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
        assertThat(port.role()).as(node.type() + "." + id).isEqualTo(role);
        assertThat(port.dataType()).as(node.type() + "." + id).isEqualTo("table");
        assertThat(port.required()).as(node.type() + "." + id).isEqualTo(required);
        assertThat(port.multiple()).as(node.type() + "." + id).isEqualTo(multiple);
    }

    private static void assertProperties(NodeDefinition node, String... names) {
        assertThat(fieldNames(node.configSchema().path("properties")))
                .as(node.type())
                .containsExactlyInAnyOrder(names);
    }

    private static void assertEnum(NodeDefinition node, String field, String... values) {
        assertEnumValues(node.configSchema().path("properties").path(field), values);
    }

    private static void assertEnumValues(JsonNode schema, String... values) {
        assertThat(schema.path("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly(values);
    }

    private static List<String> enumValues(JsonNode schema) {
        List<String> values = new ArrayList<>();
        schema.path("enum").forEach(value -> values.add(value.asText()));
        return values;
    }

    private static void assertString(NodeDefinition node, String field) {
        assertThat(node.configSchema().path("properties").path(field).path("type").asText())
                .as(node.type() + "." + field)
                .isEqualTo("string");
    }

    private static void assertStringArray(NodeDefinition node, String field) {
        JsonNode property = node.configSchema().path("properties").path(field);
        assertThat(property.path("type").asText()).as(node.type() + "." + field).isEqualTo("array");
        assertThat(property.path("items").path("type").asText()).isEqualTo("string");
    }

    private static void assertObjectArrayRequired(NodeDefinition node, String field, String... required) {
        JsonNode items = node.configSchema().path("properties").path(field).path("items");
        assertThat(items.path("type").asText()).as(node.type() + "." + field).isEqualTo("object");
        assertThat(items.path("additionalProperties").asBoolean()).isFalse();
        Set<String> requiredFields = new LinkedHashSet<>();
        items.path("required").forEach(value -> requiredFields.add(value.asText()));
        assertThat(requiredFields).containsExactlyInAnyOrder(required);
        assertThat(fieldNames(items.path("properties"))).containsExactlyInAnyOrder(required);
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
