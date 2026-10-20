package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineStage;
import com.ontoweave.data.pipeline.check.PipelineStageResolver;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineGraphValidatorTest {

    private static final UUID INPUT_A = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID INPUT_B = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID FILTER = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID FILTER_TWO = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID UNION = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID AGGREGATE = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final UUID OUTPUT = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID OUTPUT_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineNodeCatalog catalog = new PipelineNodeCatalog();
    private final PipelineGraphValidator validator = new PipelineGraphValidator();

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

        List<PipelineIssue> issues = validate(draft, Map.of(
                datasetA, schema("id", "long"),
                datasetB, schema("id", "long")
        ));

        assertThat(codes(issues)).contains("PORT_OCCUPIED");
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

        List<PipelineIssue> issues = validate(draft, Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(new FieldSchema("amount", "string", false)))
        ));

        assertThat(codes(issues)).contains("TYPE_MISMATCH");
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

        List<PipelineIssue> issues = validate(draft, Map.of(
                datasetA, schema("id", "long"),
                datasetB, schema("id", "string")
        ));

        assertThat(codes(issues)).contains("SCHEMA_MISMATCH");
    }

    @Test
    void joinPrefixesOnlyCollidingRightFields() {
        ObjectNode config = mapper.createObjectNode();
        config.put("joinType", "left");
        config.putArray("keys").addObject()
                .put("left", "id")
                .put("right", "id");

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
        config.putArray("keys").addObject()
                .put("left", "id")
                .put("right", "id");

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

        List<PipelineIssue> issues = validate(draft, Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(
                        new FieldSchema("id", "long", false),
                        new FieldSchema("amount", "string", true)
                ))
        ));

        assertThat(codes(issues)).contains("AGGREGATION_INVALID");
    }

    @Test
    void reportsMissingAndUnconnectedOutputs() {
        List<PipelineIssue> missing = validate(emptyDraft(), Map.of());
        assertThat(codes(missing)).contains("OUTPUT_MISSING");

        UUID datasetId = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(OUTPUT, "output", outputConfig(OUTPUT_ID))),
                mapper.createArrayNode()
        );
        List<PipelineIssue> unconnected = validate(draft, Map.of(datasetId, schema("id", "long")));
        assertThat(codes(unconnected)).contains("OUTPUT_NOT_CONNECTED");
    }

    @Test
    void validatesPrimaryKeysAndExpectationsAgainstOutputSchema() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode draft = outputDraft(datasetId);
        Map<UUID, InputSchema> schemas = Map.of(datasetId, schema("id", "long"));

        ObjectNode primaryKeyConfig = mapper.createObjectNode();
        primaryKeyConfig.putArray("primaryKeyColumns").add("missing");
        primaryKeyConfig.putArray("expectations");
        List<PipelineIssue> primaryKeyIssues = validator.validate(
                PipelineDraftGraph.from(draft),
                catalog,
                schemas,
                Set.of(OUTPUT_ID),
                Map.of(OUTPUT_ID, new OutputConfiguration(primaryKeyConfig))
        );
        assertThat(codes(primaryKeyIssues)).contains("PRIMARY_KEY_INVALID");
        assertThat(primaryKeyIssues)
                .filteredOn(issue -> issue.code().equals("PRIMARY_KEY_INVALID"))
                .extracting(PipelineIssue::field)
                .contains("missing");

        ObjectNode expectationConfig = mapper.createObjectNode();
        expectationConfig.putArray("primaryKeyColumns").add("id");
        ObjectNode rowCount = expectationConfig.putArray("expectations").addObject();
        rowCount.put("type", "rowCount");
        rowCount.put("min", 10);
        rowCount.put("max", 1);
        List<PipelineIssue> expectationIssues = validator.validate(
                PipelineDraftGraph.from(draft),
                catalog,
                schemas,
                Set.of(OUTPUT_ID),
                Map.of(OUTPUT_ID, new OutputConfiguration(expectationConfig))
        );
        assertThat(codes(expectationIssues)).contains("EXPECTATION_INVALID");
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

        List<PipelineIssue> issues = validate(draft, Map.of(datasetId, schema("id", "long")));

        assertThat(codes(issues)).contains("EDGE_PORT_INVALID", "NODE_CONFIG_INCOMPLETE");
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

        List<PipelineIssue> issues = validate(draft, Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(
                        new FieldSchema("id", "long", false),
                        new FieldSchema("name", "string", true)
                ))
        ));

        assertThat(codes(issues)).contains("DUPLICATE_FIELD");
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

        List<PipelineIssue> issues = validate(malformed, Map.of());

        assertThat(issues).anySatisfy(issue -> {
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

    @Test
    void resolvesStagePriorityInContractOrder() {
        PipelineEntity pipeline = mock(PipelineEntity.class);
        PipelineBuildRepository builds = mock(PipelineBuildRepository.class);
        PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
        PipelineStageResolver resolver = new PipelineStageResolver(builds, versions);
        UUID pipelineId = UUID.randomUUID();
        when(pipeline.getId()).thenReturn(pipelineId);
        when(builds.existsByPipelineIdAndStatusIn(eq(pipelineId), anyList())).thenReturn(true);

        assertThat(resolver.resolve(pipeline, 5, true, false, false, true))
                .isEqualTo(PipelineStage.BUILDING);

        when(builds.existsByPipelineIdAndStatusIn(eq(pipelineId), anyList())).thenReturn(false);
        assertThat(resolver.resolve(pipeline, 5, true, false, false, true))
                .isEqualTo(PipelineStage.EMPTY);
        assertThat(resolver.resolve(pipeline, 5, false, false, true, true))
                .isEqualTo(PipelineStage.NEEDS_INPUT);
        assertThat(resolver.resolve(pipeline, 5, false, true, false, true))
                .isEqualTo(PipelineStage.NEEDS_OUTPUT);
        assertThat(resolver.resolve(pipeline, 5, false, true, true, true))
                .isEqualTo(PipelineStage.INVALID);

        UUID failedVersionId = UUID.randomUUID();
        PipelineBuildEntity failed = mock(PipelineBuildEntity.class);
        PipelineVersionEntity failedVersion = mock(PipelineVersionEntity.class);
        when(failed.getStatus()).thenReturn(PipelineBuildStatus.FAILED);
        when(failed.getPipelineVersionId()).thenReturn(failedVersionId);
        when(failedVersion.getSourceDraftRevision()).thenReturn(5L);
        when(builds.findTopByPipelineIdOrderByCreatedAtDesc(pipelineId))
                .thenReturn(Optional.of(failed));
        when(versions.findById(failedVersionId)).thenReturn(Optional.of(failedVersion));
        assertThat(resolver.resolve(pipeline, 5, false, true, true, false))
                .isEqualTo(PipelineStage.FAILED);

        UUID currentVersionId = UUID.randomUUID();
        PipelineVersionEntity current = mock(PipelineVersionEntity.class);
        when(pipeline.getCurrentVersionId()).thenReturn(currentVersionId);
        when(versions.findById(currentVersionId)).thenReturn(Optional.of(current));
        when(builds.findTopByPipelineIdOrderByCreatedAtDesc(pipelineId)).thenReturn(Optional.empty());
        when(current.getSourceDraftRevision()).thenReturn(4L);
        assertThat(resolver.resolve(pipeline, 5, false, true, true, false))
                .isEqualTo(PipelineStage.STALE);
        when(current.getSourceDraftRevision()).thenReturn(5L);
        assertThat(resolver.resolve(pipeline, 5, false, true, true, false))
                .isEqualTo(PipelineStage.SUCCEEDED);
        when(pipeline.getCurrentVersionId()).thenReturn(null);
        assertThat(resolver.resolve(pipeline, 5, false, true, true, false))
                .isEqualTo(PipelineStage.READY);

        assertThat(mapper.valueToTree(PipelineStage.NEEDS_INPUT).asText()).isEqualTo("needs_input");
    }

    private List<PipelineIssue> validate(ObjectNode draft, Map<UUID, InputSchema> schemas) {
        return validator.validate(PipelineDraftGraph.from(draft), catalog, schemas, Set.of());
    }

    private List<String> codes(List<PipelineIssue> issues) {
        return issues.stream().map(PipelineIssue::code).toList();
    }

    private ObjectNode outputDraft(UUID datasetId) {
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(OUTPUT, "output", outputConfig(OUTPUT_ID))),
                mapper.createArrayNode().add(edge(INPUT_A, "out", OUTPUT, "in"))
        );
        return draft;
    }

    private ObjectNode emptyDraft() {
        return draft(mapper.createArrayNode(), mapper.createArrayNode());
    }

    private ObjectNode draft(ArrayNode nodes, ArrayNode edges) {
        ObjectNode draft = mapper.createObjectNode();
        draft.put("schemaVersion", 1);
        draft.set("nodes", nodes);
        draft.set("edges", edges);
        return draft;
    }

    private ObjectNode node(UUID id, String type, ObjectNode config) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", id.toString());
        node.put("type", type);
        node.put("name", type + "-" + id);
        node.set("config", config);
        ObjectNode position = node.putObject("position");
        position.put("x", 10);
        position.put("y", 20);
        return node;
    }

    private ObjectNode edge(UUID source, String sourcePort, UUID target, String targetPort) {
        ObjectNode edge = mapper.createObjectNode();
        edge.put("id", UUID.randomUUID().toString());
        edge.put("sourceNodeId", source.toString());
        edge.put("sourcePort", sourcePort);
        edge.put("targetNodeId", target.toString());
        edge.put("targetPort", targetPort);
        return edge;
    }

    private ObjectNode inputConfig(UUID datasetId) {
        ObjectNode config = mapper.createObjectNode();
        config.put("datasetKind", "sync");
        config.put("datasetId", datasetId.toString());
        return config;
    }

    private ObjectNode filterConfig(String field, String dataType) {
        try {
            return (ObjectNode) mapper.readTree("""
                    {"condition":{"kind":"compare","field":"%s","op":"gt",
                     "value":{"kind":"literal","dataType":"%s","value":10}}}
                    """.formatted(field, dataType));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private ObjectNode outputConfig(UUID outputId) {
        ObjectNode config = mapper.createObjectNode();
        config.put("outputId", outputId.toString());
        return config;
    }

    private InputSchema schema(String name, String type) {
        return new InputSchema(
                UUID.randomUUID(),
                List.of(new FieldSchema(name, type, true))
        );
    }
}
