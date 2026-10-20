package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineGraphValidatorTest extends PipelineGraphValidatorTestSupport {

    @Test
    void reportsCycleAsGraphIssue() {
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(FILTER, "filter", mapper.createObjectNode()))
                        .add(node(FILTER_TWO, "filter", mapper.createObjectNode())),
                mapper.createArrayNode()
                        .add(edge(FILTER, "out", FILTER_TWO, "in"))
                        .add(edge(FILTER_TWO, "out", FILTER, "in"))
        );

        List<PipelineIssue> issues = validate(draft, Map.of());

        assertThat(codes(issues)).contains("CYCLE_DETECTED");
        assertThat(issues).filteredOn(issue -> issue.code().equals("CYCLE_DETECTED"))
                .allSatisfy(issue -> {
                    assertThat(issue.phase()).isEqualTo("graph");
                    assertThat(issue.blocking()).isTrue();
                });
    }

    @Test
    void reportsFieldNotFoundForFilterCondition() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(FILTER, "filter", filterConfig("amount", "number"))),
                mapper.createArrayNode().add(edge(INPUT_A, "out", FILTER, "in"))
        );

        List<PipelineIssue> issues = validate(draft, Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(new FieldSchema("id", "long", false)))
        ));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("FIELD_NOT_FOUND");
            assertThat(issue.phase()).isEqualTo("schema");
            assertThat(issue.nodeId()).isEqualTo(FILTER.toString());
            assertThat(issue.field()).isEqualTo("amount");
            assertThat(issue.blocking()).isTrue();
        });
    }

    @Test
    void reportsPortOccupiedForSecondEdgeIntoOneInputPort() {
        UUID datasetA = UUID.randomUUID();
        UUID datasetB = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetA)))
                        .add(node(INPUT_B, "input", inputConfig(datasetB)))
                        .add(node(FILTER, "filter", mapper.createObjectNode())),
                mapper.createArrayNode()
                        .add(edge(INPUT_A, "out", FILTER, "in"))
                        .add(edge(INPUT_B, "out", FILTER, "in"))
        );

        assertThat(codes(validate(draft, Map.of(
                datasetA, schema("id", "long"),
                datasetB, schema("id", "long")
        )))).contains("PORT_OCCUPIED");
    }

    @Test
    void reportsTypeMismatchInFilterComparison() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(FILTER, "filter", filterConfig("amount", "number"))),
                mapper.createArrayNode().add(edge(INPUT_A, "out", FILTER, "in"))
        );

        assertThat(codes(validate(draft, Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(new FieldSchema("amount", "string", false)))
        )))).contains("TYPE_MISMATCH");
    }

    @Test
    void reportsUnionSchemaMismatch() {
        UUID datasetA = UUID.randomUUID();
        UUID datasetB = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetA)))
                        .add(node(INPUT_B, "input", inputConfig(datasetB)))
                        .add(node(UNION, "union", mapper.createObjectNode())),
                mapper.createArrayNode()
                        .add(edge(INPUT_A, "out", UNION, "inputs"))
                        .add(edge(INPUT_B, "out", UNION, "inputs"))
        );

        assertThat(codes(validate(draft, Map.of(
                datasetA, schema("id", "long"),
                datasetB, schema("id", "string")
        )))).contains("SCHEMA_MISMATCH");
    }

    @Test
    void joinPrefixesOnlyCollidingRightFields() {
        ObjectNode config = mapper.createObjectNode();
        config.put("joinType", "left");
        config.putArray("keys").addObject().put("left", "id").put("right", "id");

        List<FieldSchema> output = new RelationalSchemaDeriver(FILTER.toString(), new ArrayList<>())
                .join(
                        config,
                        List.of(new FieldSchema("id", "long", false),
                                new FieldSchema("name", "string", true)),
                        List.of(new FieldSchema("id", "long", false),
                                new FieldSchema("dept", "string", true))
                );

        assertThat(output).extracting(FieldSchema::name)
                .containsExactly("id", "name", "right_id", "dept");
    }

    @Test
    void joinSuffixesRepeatedRightCollisions() {
        ObjectNode config = mapper.createObjectNode();
        config.put("joinType", "left");
        config.putArray("keys").addObject().put("left", "id").put("right", "id");

        List<FieldSchema> output = new RelationalSchemaDeriver(FILTER.toString(), new ArrayList<>())
                .join(
                        config,
                        List.of(new FieldSchema("id", "long", false),
                                new FieldSchema("right_id", "long", false),
                                new FieldSchema("name", "string", true)),
                        List.of(new FieldSchema("id", "long", false),
                                new FieldSchema("id", "long", false),
                                new FieldSchema("dept", "string", true))
                );

        assertThat(output).extracting(FieldSchema::name)
                .containsExactly("id", "right_id", "name", "right_id_2", "right_id_3", "dept");
    }

    @Test
    void reportsInvalidAggregationType() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode config = mapper.createObjectNode();
        config.putArray("groupBy").add("id");
        ObjectNode aggregation = config.putArray("aggregations").addObject();
        aggregation.put("op", "sum");
        aggregation.put("field", "amount");
        aggregation.put("outputField", "total");
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(AGGREGATE, "aggregate", config)),
                mapper.createArrayNode().add(edge(INPUT_A, "out", AGGREGATE, "in"))
        );

        assertThat(codes(validate(draft, Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(
                        new FieldSchema("id", "long", false),
                        new FieldSchema("amount", "string", true)
                ))
        )))).contains("AGGREGATION_INVALID");
    }

    @Test
    void reportsIncompleteNodeConfigAndInvalidEdgePort() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(FILTER, "filter", mapper.createObjectNode())),
                mapper.createArrayNode().add(edge(INPUT_A, "missing-port", FILTER, "in"))
        );

        assertThat(codes(validate(draft, Map.of(datasetId, schema("id", "long")))))
                .contains("EDGE_PORT_INVALID", "NODE_CONFIG_INCOMPLETE");
    }

    @Test
    void reportsDuplicateFieldWhenRenameCollides() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode config = mapper.createObjectNode();
        ObjectNode rename = config.putArray("renames").addObject();
        rename.put("from", "id");
        rename.put("to", "name");
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(FILTER, "rename_columns", config)),
                mapper.createArrayNode().add(edge(INPUT_A, "out", FILTER, "in"))
        );

        assertThat(codes(validate(draft, Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(
                        new FieldSchema("id", "long", false),
                        new FieldSchema("name", "string", true)
                ))
        )))).contains("DUPLICATE_FIELD");
    }

    @Test
    void reportsMissingRequiredInputAndUnresolvedInputVersion() {
        ObjectNode missingInput = draft(
                mapper.createArrayNode().add(node(FILTER, "filter", mapper.createObjectNode())),
                mapper.createArrayNode()
        );
        assertThat(codes(validate(missingInput, Map.of()))).contains("INPUT_MISSING");

        UUID datasetId = UUID.randomUUID();
        ObjectNode unresolved = draft(
                mapper.createArrayNode().add(node(INPUT_A, "input", inputConfig(datasetId))),
                mapper.createArrayNode()
        );
        assertThat(codes(validate(unresolved, Map.of()))).contains("INPUT_VERSION_UNRESOLVED");
    }

    @Test
    void reportsMalformedIdentityInStructurePhase() {
        ObjectNode malformed = mapper.createObjectNode();
        malformed.put("schemaVersion", 1);
        ObjectNode rawNode = malformed.putArray("nodes").addObject();
        rawNode.put("id", "not-a-uuid");
        rawNode.put("type", "filter");
        rawNode.put("name", "Filter");
        rawNode.putObject("config");
        rawNode.putObject("position").put("x", 1).put("y", 2);
        malformed.putArray("edges");

        assertThat(validate(malformed, Map.of())).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("CONFIG_INVALID");
            assertThat(issue.phase()).isEqualTo("structure");
            assertThat(issue.blocking()).isTrue();
        });
    }

    @Test
    void rejectsNonIntegralSchemaVersionInStructurePhase() {
        for (JsonNode schemaVersion : List.of(
                mapper.getNodeFactory().textNode("1"),
                mapper.getNodeFactory().numberNode(1.5),
                mapper.getNodeFactory().booleanNode(true)
        )) {
            ObjectNode draft = emptyDraft();
            draft.set("schemaVersion", schemaVersion);
            assertThat(validate(draft, Map.of())).anySatisfy(issue -> {
                assertThat(issue.code()).isEqualTo("CONFIG_INVALID");
                assertThat(issue.phase()).isEqualTo("structure");
                assertThat(issue.message()).contains("schemaVersion");
            });
        }
    }

    @Test
    void reportsMissingFieldsInAggregateGroupingAndCount() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode config = mapper.createObjectNode();
        config.putArray("groupBy").add("missing_group");
        ObjectNode aggregation = config.putArray("aggregations").addObject();
        aggregation.put("op", "count");
        aggregation.put("field", "missing_count");
        aggregation.put("outputField", "total");
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(AGGREGATE, "aggregate", config)),
                mapper.createArrayNode().add(edge(INPUT_A, "out", AGGREGATE, "in"))
        );

        List<PipelineIssue> issues = validate(draft, Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(new FieldSchema("id", "long", false)))
        ));

        assertThat(issues).filteredOn(issue -> issue.code().equals("FIELD_NOT_FOUND"))
                .extracting(PipelineIssue::field)
                .contains("missing_group", "missing_count");
    }
}
