package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.source.DisplaySnapshot;
import com.ontoweave.data.pipeline.execution.build.PipelineInputResolver;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreviewPlanBuilderTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final long REVISION = 4L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final PipelineDraftRepository drafts = mock(PipelineDraftRepository.class);
    private final PipelineInputResolver inputResolver = mock(PipelineInputResolver.class);
    private final PreviewPlanBuilder builder = new PreviewPlanBuilder(pipelines, drafts, inputResolver);

    @Test
    void keepsOnlyTargetUpstreamSubgraph() {
        UUID ordersNode = UUID.randomUUID();
        UUID filterNode = UUID.randomUUID();
        UUID targetNode = UUID.randomUUID();
        UUID customersNode = UUID.randomUUID();
        UUID unrelatedNode = UUID.randomUUID();
        ResolvedInput orders = input(UUID.randomUUID(), 100L);
        ObjectNode definition = definition();
        ArrayNode nodes = definition.withArray("nodes");
        nodes.add(inputNode(ordersNode, orders.datasetId()));
        nodes.add(transformNode(filterNode, "filter", filterConfig()));
        nodes.add(transformNode(targetNode, "derive_column", deriveConfig()));
        nodes.add(inputNode(customersNode, UUID.randomUUID()));
        nodes.add(transformNode(unrelatedNode, "select_columns", selectConfig()));
        ArrayNode edges = definition.withArray("edges");
        edges.add(edge(ordersNode, "out", filterNode, "in"));
        edges.add(edge(filterNode, "out", targetNode, "in"));
        edges.add(edge(customersNode, "out", unrelatedNode, "in"));
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(new PipelineEntity(PIPELINE_ID, OWNER_ID, UUID.randomUUID(), "orders")));
        PipelineDraftEntity draft = draft(definition);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(inputResolver.resolve(PIPELINE_ID, OWNER_ID, REVISION)).thenReturn(List.of(orders));

        LogicalPlan plan = builder.build(PIPELINE_ID, OWNER_ID, REVISION, targetNode.toString());

        assertThat(plan.root().nodeType()).isEqualTo("derive_column");
        assertThat(plan.nodes()).extracting(PlanStep::nodeType)
                .containsExactlyInAnyOrder("derive_column", "filter", "input");
        assertThat(plan.nodes()).extracting(PlanStep::nodeId)
                .doesNotContain(unrelatedNode.toString(), customersNode.toString());
        assertThat(plan.inputVersions()).containsExactly(orders);
    }

    @Test
    void inputPlanCarriesFrozenSnapshotAndScanLimit() {
        UUID ordersNode = UUID.randomUUID();
        ResolvedInput orders = input(UUID.randomUUID(), 100L);
        ObjectNode definition = definition();
        definition.withArray("nodes").add(inputNode(ordersNode, orders.datasetId()));
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(new PipelineEntity(PIPELINE_ID, OWNER_ID, UUID.randomUUID(), "orders")));
        PipelineDraftEntity draft = draft(definition);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(inputResolver.resolve(PIPELINE_ID, OWNER_ID, REVISION)).thenReturn(List.of(orders));

        LogicalPlan plan = builder.build(PIPELINE_ID, OWNER_ID, REVISION, ordersNode.toString());

        assertThat(plan.root().node()).isInstanceOfSatisfying(
                LogicalPlanNode.ScanInput.class,
                scan -> {
                    assertThat(scan.snapshotId()).isEqualTo(100L);
                    assertThat(scan.scanLimit()).isEqualTo(ScanLimit.preview(50_000));
                    assertThat(scan.catalog()).isEqualTo("ontoweave");
                    assertThat(scan.namespace()).isEqualTo("sales");
                    assertThat(scan.table()).isEqualTo("orders");
                }
        );
    }

    @Test
    void joinKeepsBothUpstreamBranches() {
        UUID leftNode = UUID.randomUUID();
        UUID rightNode = UUID.randomUUID();
        UUID joinNode = UUID.randomUUID();
        ResolvedInput left = input(UUID.randomUUID(), 100L);
        ResolvedInput right = input(UUID.randomUUID(), 200L);
        ObjectNode definition = definition();
        ArrayNode nodes = definition.withArray("nodes");
        nodes.add(inputNode(leftNode, left.datasetId()));
        nodes.add(inputNode(rightNode, right.datasetId()));
        ObjectNode join = transformNode(joinNode, "join", mapper.createObjectNode()
                .put("joinType", "inner")
                .set("keys", mapper.createArrayNode().add(mapper.createObjectNode()
                        .put("left", "customer_id")
                        .put("right", "id"))));
        nodes.add(join);
        ArrayNode edges = definition.withArray("edges");
        edges.add(edge(leftNode, "out", joinNode, "left"));
        edges.add(edge(rightNode, "out", joinNode, "right"));
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(new PipelineEntity(PIPELINE_ID, OWNER_ID, UUID.randomUUID(), "orders")));
        PipelineDraftEntity draft = draft(definition);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(inputResolver.resolve(PIPELINE_ID, OWNER_ID, REVISION)).thenReturn(List.of(left, right));

        LogicalPlan plan = builder.build(PIPELINE_ID, OWNER_ID, REVISION, joinNode.toString());

        assertThat(plan.root().inputs()).hasSize(2);
        assertThat(plan.inputVersions()).containsExactly(left, right);
    }

    @Test
    void buildPlanLeavesEveryScanUnlimited() {
        UUID leftNode = UUID.randomUUID();
        UUID rightNode = UUID.randomUUID();
        UUID joinNode = UUID.randomUUID();
        ResolvedInput left = input(UUID.randomUUID(), 100L);
        ResolvedInput right = input(UUID.randomUUID(), 200L);
        ObjectNode definition = definition();
        definition.withArray("nodes").add(inputNode(leftNode, left.datasetId()));
        definition.withArray("nodes").add(inputNode(rightNode, right.datasetId()));
        definition.withArray("nodes").add(transformNode(joinNode, "join", mapper.createObjectNode()
                .put("joinType", "inner")
                .set("keys", mapper.createArrayNode().add(mapper.createObjectNode()
                        .put("left", "customer_id")
                        .put("right", "id")))));
        definition.withArray("edges").add(edge(leftNode, "out", joinNode, "left"));
        definition.withArray("edges").add(edge(rightNode, "out", joinNode, "right"));
        PipelineVersionEntity version = new PipelineVersionEntity(
                UUID.randomUUID(), PIPELINE_ID, 2, definition, mapper.createArrayNode(),
                "hash", mapper.createArrayNode(), REVISION, OWNER_ID
        );

        LogicalPlan plan = builder.buildForBuild(version, List.of(left, right), joinNode.toString());

        List<LogicalPlanNode.ScanInput> scans = plan.nodes().stream()
                .map(PlanStep::node)
                .filter(LogicalPlanNode.ScanInput.class::isInstance)
                .map(LogicalPlanNode.ScanInput.class::cast)
                .toList();
        assertThat(scans).hasSize(2)
                .allSatisfy(scan -> assertThat(scan.scanLimit().limited()).isFalse());
    }

    private PipelineDraftEntity draft(ObjectNode definition) {
        PipelineDraftEntity draft = mock(PipelineDraftEntity.class);
        when(draft.getRevision()).thenReturn(REVISION);
        when(draft.getDefinition()).thenReturn(definition);
        return draft;
    }

    private ObjectNode definition() {
        ObjectNode definition = mapper.createObjectNode();
        definition.put("schemaVersion", 1);
        definition.putArray("nodes");
        definition.putArray("edges");
        return definition;
    }

    private ObjectNode inputNode(UUID nodeId, UUID datasetId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", nodeId.toString());
        node.put("type", "input");
        node.put("name", "input-" + datasetId);
        node.putObject("config")
                .put("datasetKind", "sync")
                .put("datasetId", datasetId.toString());
        return node;
    }

    private ObjectNode transformNode(UUID nodeId, String type, ObjectNode config) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", nodeId.toString());
        node.put("type", type);
        node.put("name", type);
        node.set("config", config);
        return node;
    }

    private ObjectNode filterConfig() {
        return mapper.createObjectNode().set("condition", mapper.createObjectNode()
                .put("kind", "compare")
                .put("field", "id")
                .put("op", "gt")
                .set("value", mapper.createObjectNode()
                        .put("kind", "literal")
                        .put("dataType", "long")
                        .put("value", 10)));
    }

    private ObjectNode deriveConfig() {
        return mapper.createObjectNode()
                .put("outputField", "amount_text")
                .set("expression", mapper.createObjectNode()
                        .put("kind", "cast")
                        .put("dataType", "string")
                        .set("expression", mapper.createObjectNode()
                                .put("kind", "field")
                                .put("field", "amount")));
    }

    private ObjectNode selectConfig() {
        return mapper.createObjectNode()
                .put("mode", "include")
                .set("columns", mapper.createArrayNode().add("id"));
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

    private ResolvedInput input(UUID datasetId, long snapshotId) {
        return new ResolvedInput(
                "sync",
                datasetId,
                UUID.randomUUID(),
                12,
                "schema-hash",
                "ontoweave",
                "sales",
                "orders",
                snapshotId,
                new DisplaySnapshot("orders", 12, UUID.randomUUID(), "prod-mysql", "sales", "orders")
        );
    }
}
