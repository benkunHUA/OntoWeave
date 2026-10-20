package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ConfigSchemasTest {

    private static final String LITERAL =
            "{\"kind\":\"literal\",\"dataType\":\"string\",\"value\":\"active\"}";

    private final PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsEmptyAndPartialTopLevelConfigs() throws Exception {
        for (NodeDefinition node : catalog.nodes()) {
            assertValid(node, "{}");
            assertValidByType(node.type(), "{}");
        }
        assertValid(catalog.require("select_columns"), "{\"mode\":\"include\"}");
        assertValid(catalog.require("join"), "{\"joinType\":\"left\"}");
    }

    @ParameterizedTest(name = "accepts filter operator {0}")
    @MethodSource("validFilterOperators")
    void acceptsEveryFilterOperator(String operator, String config) throws Exception {
        assertValid(catalog.require("filter"), config);
    }

    @Test
    void acceptsNestedAndOrNullConditions() throws Exception {
        assertValid(catalog.require("filter"), """
                {"condition":{"kind":"and","conditions":[
                  {"kind":"compare","field":"deleted_at","op":"is_null"},
                  {"kind":"or","conditions":[
                    {"kind":"compare","field":"status","op":"is_not_null"},
                    {"kind":"compare","field":"region","op":"eq","value":%s}
                  ]}
                ]}}
                """.formatted(LITERAL));
    }

    @Test
    void rejectsMalformedRecursiveOperands() throws Exception {
        assertInvalid(catalog.require("filter"), nestedAnd("""
                {"kind":"compare","field":"status","op":"eq","value":{"sql":"select 1"}}
                """));
        assertInvalid(catalog.require("filter"), nestedAnd("""
                {"kind":"compare","field":"status","op":"eq","value":{"kind":"literal","value":"active"}}
                """));
        assertInvalid(catalog.require("filter"), nestedAnd("""
                {"kind":"compare","field":"status","op":"eq","value":{"kind":"literal","dataType":"string"}}
                """));
        assertInvalid(catalog.require("filter"), nestedAnd("""
                {"kind":"compare","field":"status","op":"in","values":[{"sql":"select 1"}]}
                """));
    }

    @Test
    void rejectsInvalidUuidsAndAcceptsValidOnes() throws Exception {
        assertInvalid(catalog.require("input"), "{\"datasetId\":\"not-a-uuid\"}");
        assertInvalid(catalog.require("output"), "{\"outputId\":\"not-a-uuid\"}");
        UUID id = UUID.randomUUID();
        assertValid(catalog.require("input"),
                "{\"datasetKind\":\"sync\",\"datasetId\":\"" + id + "\"}");
        assertValid(catalog.require("output"), "{\"outputId\":\"" + id + "\"}");
    }

    @ParameterizedTest(name = "rejects invalid expression {0}")
    @MethodSource("invalidExpressions")
    void rejectsInvalidExpressionKindAndOperatorCombinations(String name, String expression)
            throws Exception {
        assertInvalid(catalog.require("derive_column"), derive(expression));
    }

    @ParameterizedTest(name = "accepts valid expression {0}")
    @MethodSource("validExpressions")
    void acceptsEveryExpressionKind(String name, String expression) throws Exception {
        assertValid(catalog.require("derive_column"), derive(expression));
    }

    @Test
    void rejectsStrictNestedConfigsOncePresent() throws Exception {
        assertInvalid(catalog.require("filter"), "{\"condition\":{}}");
        assertInvalid(catalog.require("derive_column"), "{\"expression\":{}}");
        assertInvalid(catalog.require("select_columns"), "{\"mode\":\"other\"}");
    }

    @Test
    void cachesSchemasByTypeVersionAndSchemaHash() throws Exception {
        ObjectNode firstSchema = (ObjectNode) mapper.readTree("""
                {"type":"object","additionalProperties":false,
                 "properties":{"first":{"type":"string"}}}
                """);
        ObjectNode secondSchema = (ObjectNode) mapper.readTree("""
                {"type":"object","additionalProperties":false,
                 "properties":{"second":{"type":"string"}}}
                """);
        NodeDefinition first = definition("filter", 1, firstSchema);
        NodeDefinition second = definition("filter", 1, secondSchema);

        assertInvalid(first, "{\"second\":\"accepted-only-by-v2\"}");
        assertValid(second, "{\"second\":\"accepted-only-by-v2\"}");
        assertValid(first, "{\"first\":\"still-v1\"}");
    }

    @Test
    void mapsUnknownTypesToNodeTypeUnknown() throws Exception {
        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> ConfigSchemas.validate("run", mapper.readTree("{}"))
        );
        assertThat(exception).isNotNull();
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.code()).isEqualTo("NODE_TYPE_UNKNOWN");
    }

    @Test
    void schemasAndResponsesAreDefensivelyCopied() {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        String version = catalog.catalogVersion();
        NodeDefinition filter = catalog.require("filter");
        filter.configSchema().put("type", "string");
        filter.uiSchema().put("fields", "broken");

        assertThat(catalog.catalogVersion()).isEqualTo(version);
        assertThat(catalog.require("filter").configSchema().path("type").asText()).isEqualTo("object");
        NodeCatalogResponse response = catalog.response();
        assertThat(catchThrowableOfType(UnsupportedOperationException.class, () -> response.nodes().clear()))
                .isNotNull();
    }

    static Stream<Arguments> validFilterOperators() {
        return Stream.of(
                Arguments.of("eq", compare("eq", ",\"value\":" + LITERAL)),
                Arguments.of("ne", compare("ne", ",\"value\":" + LITERAL)),
                Arguments.of("gt", compare("gt", ",\"value\":" + LITERAL)),
                Arguments.of("gte", compare("gte", ",\"value\":" + LITERAL)),
                Arguments.of("lt", compare("lt", ",\"value\":" + LITERAL)),
                Arguments.of("lte", compare("lte", ",\"value\":" + LITERAL)),
                Arguments.of("is_null", compare("is_null", "")),
                Arguments.of("is_not_null", compare("is_not_null", "")),
                Arguments.of("in", compare("in", ",\"values\":[" + LITERAL + "]"))
        );
    }

    static Stream<Arguments> invalidExpressions() {
        return Stream.of(
                Arguments.of("compare with logical op",
                        "{\"kind\":\"compare\",\"op\":\"and\",\"left\":%s,\"right\":%s}"
                                .formatted(LITERAL, LITERAL)),
                Arguments.of("logical with comparison op",
                        "{\"kind\":\"logical\",\"op\":\"eq\",\"expressions\":[%s,%s]}"
                                .formatted(LITERAL, LITERAL)),
                Arguments.of("logical with one expression",
                        "{\"kind\":\"logical\",\"op\":\"and\",\"expressions\":[%s]}"
                                .formatted(LITERAL)),
                Arguments.of("field with op",
                        "{\"kind\":\"field\",\"field\":\"amount\",\"op\":\"eq\"}"),
                Arguments.of("literal missing dataType",
                        "{\"kind\":\"literal\",\"value\":1}"),
                Arguments.of("literal missing value",
                        "{\"kind\":\"literal\",\"dataType\":\"number\"}"),
                Arguments.of("cast missing dataType",
                        "{\"kind\":\"cast\",\"expression\":%s}".formatted(LITERAL)),
                Arguments.of("case missing otherwise",
                        "{\"kind\":\"case\",\"branches\":[{\"when\":%s,\"then\":%s}]}"
                                .formatted(LITERAL, LITERAL)),
                Arguments.of("isNull with undocumented negated",
                        "{\"kind\":\"isNull\",\"expression\":%s,\"negated\":true}"
                                .formatted(LITERAL)),
                Arguments.of("unknown kind", "{\"kind\":\"sql\",\"value\":\"select 1\"}")
        );
    }

    static Stream<Arguments> validExpressions() {
        return Stream.of(
                Arguments.of("field", "{\"kind\":\"field\",\"field\":\"amount\"}"),
                Arguments.of("literal", LITERAL),
                Arguments.of("cast", "{\"kind\":\"cast\",\"expression\":%s,\"dataType\":\"number\"}"
                        .formatted(LITERAL)),
                Arguments.of("case", """
                        {"kind":"case","branches":[{"when":%s,"then":%s}],"otherwise":%s}
                        """.formatted(LITERAL, LITERAL, LITERAL)),
                Arguments.of("compare", """
                        {"kind":"compare","op":"gt","left":%s,"right":%s}
                        """.formatted(LITERAL, LITERAL)),
                Arguments.of("logical", """
                        {"kind":"logical","op":"and","expressions":[%s,%s]}
                        """.formatted(LITERAL, LITERAL)),
                Arguments.of("isNull", "{\"kind\":\"isNull\",\"expression\":%s}".formatted(LITERAL))
        );
    }

    private static String compare(String operator, String fields) {
        return "{\"condition\":{\"kind\":\"compare\",\"field\":\"status\",\"op\":\""
                + operator + "\"" + fields + "}}";
    }

    private static String nestedAnd(String condition) {
        return "{\"condition\":{\"kind\":\"and\",\"conditions\":[" + condition + ","
                + compareCondition("is_null") + "]}}";
    }

    private static String compareCondition(String operator) {
        return "{\"kind\":\"compare\",\"field\":\"other\",\"op\":\"" + operator + "\"}";
    }

    private static String derive(String expression) {
        return "{\"outputField\":\"result\",\"expression\":" + expression + "}";
    }

    private static NodeDefinition definition(String type, int version, ObjectNode schema) {
        return new NodeDefinition(
                type, version, "测试节点", "transform", List.of(), schema, new ObjectMapper().createObjectNode()
        );
    }

    private void assertValid(NodeDefinition node, String config) throws Exception {
        ConfigSchemas.validate(node, mapper.readTree(config));
    }

    private void assertValidByType(String type, String config) throws Exception {
        ConfigSchemas.validate(type, mapper.readTree(config));
    }

    private void assertInvalid(NodeDefinition node, String config) throws Exception {
        assertInvalid(node, mapper.readTree(config));
    }

    private void assertInvalid(NodeDefinition node, JsonNode config) {
        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> ConfigSchemas.validate(node, config)
        );
        assertThat(exception).isNotNull();
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.code()).isEqualTo("CONFIG_INVALID");
        assertThat(exception.details()).containsKey("errors");
    }
}
