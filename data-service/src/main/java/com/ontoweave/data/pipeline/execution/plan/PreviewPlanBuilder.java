package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
import com.ontoweave.data.pipeline.execution.build.PipelineInputResolver;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.graph.DraftEdge;
import com.ontoweave.data.pipeline.graph.DraftNode;
import com.ontoweave.data.pipeline.graph.PipelineDraftGraph;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PreviewPlanBuilder {

    private final PipelineRepository pipelines;
    private final PipelineDraftRepository drafts;
    private final PipelineInputResolver inputResolver;

    public PreviewPlanBuilder(
            PipelineRepository pipelines,
            PipelineDraftRepository drafts,
            PipelineInputResolver inputResolver
    ) {
        this.pipelines = pipelines;
        this.drafts = drafts;
        this.inputResolver = inputResolver;
    }

    public LogicalPlan build(UUID pipelineId, UUID ownerId, long revision, String nodeId) {
        pipelines.findByIdAndOwnerId(pipelineId, ownerId)
                .orElseThrow(() -> notFound("流程不存在"));
        PipelineDraftEntity draft = drafts.findById(pipelineId)
                .orElseThrow(() -> notFound("流程草稿不存在"));
        if (draft.getRevision() != revision) {
            throw new PipelineRevisionConflict();
        }
        PipelineDraftGraph graph = PipelineDraftGraph.from(draft.getDefinition());
        Map<UUID, DraftNode> nodes = new LinkedHashMap<>();
        for (DraftNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        UUID targetId = uuid(nodeId);
        DraftNode target = targetId == null ? null : nodes.get(targetId);
        if (target == null) {
            throw notFound("目标节点不存在");
        }

        Map<UUID, ResolvedInput> inputsByDataset = new HashMap<>();
        for (ResolvedInput input : inputResolver.resolve(pipelineId, ownerId, revision)) {
            inputsByDataset.put(input.datasetId(), input);
        }
        Map<UUID, List<DraftEdge>> incoming = incoming(graph.edges());
        Map<String, ResolvedInput> planInputs = new LinkedHashMap<>();
        PlanStep root = buildStep(target, nodes, incoming, inputsByDataset, planInputs, new ArrayDeque<>());
        return LogicalPlan.of(root, revision, target.id().toString(), planInputs);
    }

    private PlanStep buildStep(
            DraftNode node,
            Map<UUID, DraftNode> nodes,
            Map<UUID, List<DraftEdge>> incoming,
            Map<UUID, ResolvedInput> inputsByDataset,
            Map<String, ResolvedInput> planInputs,
            Deque<UUID> path
    ) {
        if (path.contains(node.id())) {
            throw invalid(node, "预览计划存在环", null);
        }
        path.addLast(node.id());
        try {
            if ("input".equals(node.type())) {
                return input(node, inputsByDataset, planInputs);
            }
            if ("output".equals(node.type())) {
                return buildStep(requireInput(node, nodes, incoming, "in"), nodes, incoming,
                        inputsByDataset, planInputs, path);
            }
            return switch (node.type()) {
                case "filter" -> step(node, new LogicalPlanNode.Filter(node.config().path("condition")),
                        List.of(buildStep(requireInput(node, nodes, incoming, "in"), nodes, incoming,
                                inputsByDataset, planInputs, path)));
                case "select_columns" -> step(node, new LogicalPlanNode.SelectColumns(
                        text(node, "mode"), strings(node.config().path("columns"))
                ), List.of(buildStep(requireInput(node, nodes, incoming, "in"), nodes, incoming,
                        inputsByDataset, planInputs, path)));
                case "rename_columns" -> step(node, new LogicalPlanNode.RenameColumns(
                        renames(node.config().path("renames"))
                ), List.of(buildStep(requireInput(node, nodes, incoming, "in"), nodes, incoming,
                        inputsByDataset, planInputs, path)));
                case "derive_column" -> step(node, new LogicalPlanNode.DeriveColumn(
                        text(node, "outputField"), node.config().path("expression")
                ), List.of(buildStep(requireInput(node, nodes, incoming, "in"), nodes, incoming,
                        inputsByDataset, planInputs, path)));
                case "deduplicate" -> step(node, new LogicalPlanNode.Deduplicate(
                        text(node, "mode"), strings(node.config().path("columns"))
                ), List.of(buildStep(requireInput(node, nodes, incoming, "in"), nodes, incoming,
                        inputsByDataset, planInputs, path)));
                case "join" -> step(node, new LogicalPlanNode.Join(
                        text(node, "joinType"), joinKeys(node.config().path("keys"))
                ), List.of(
                        buildStep(requireInput(node, nodes, incoming, "left"), nodes, incoming,
                                inputsByDataset, planInputs, path),
                        buildStep(requireInput(node, nodes, incoming, "right"), nodes, incoming,
                                inputsByDataset, planInputs, path)
                ));
                case "union" -> step(node, new LogicalPlanNode.Union(), unionInputs(
                        node, nodes, incoming, inputsByDataset, planInputs, path
                ));
                case "aggregate" -> step(node, new LogicalPlanNode.Aggregate(
                        strings(node.config().path("groupBy")), aggregations(node.config().path("aggregations"))
                ), List.of(buildStep(requireInput(node, nodes, incoming, "in"), nodes, incoming,
                        inputsByDataset, planInputs, path)));
                default -> throw invalid(node, "不支持预览的节点类型: " + node.type(), null);
            };
        } finally {
            path.removeLast();
        }
    }

    private PlanStep input(
            DraftNode node,
            Map<UUID, ResolvedInput> inputsByDataset,
            Map<String, ResolvedInput> planInputs
    ) {
        UUID datasetId = uuid(text(node, "datasetId"));
        ResolvedInput input = datasetId == null ? null : inputsByDataset.get(datasetId);
        if (input == null) {
            throw invalid(node, "输入数据集没有已发布版本", "datasetId");
        }
        planInputs.put(node.id().toString(), input);
        return step(node, new LogicalPlanNode.ScanInput(
                input.icebergCatalog(),
                input.icebergNamespace(),
                input.icebergTable(),
                input.icebergSnapshotId(),
                PipelineExecutionLimits.DEFAULT_SCAN_LIMIT
        ), List.of());
    }

    private List<PlanStep> unionInputs(
            DraftNode node,
            Map<UUID, DraftNode> nodes,
            Map<UUID, List<DraftEdge>> incoming,
            Map<UUID, ResolvedInput> inputsByDataset,
            Map<String, ResolvedInput> planInputs,
            Deque<UUID> path
    ) {
        List<DraftEdge> edges = incoming.getOrDefault(node.id(), List.of()).stream()
                .filter(edge -> "inputs".equals(edge.targetPort()))
                .toList();
        if (edges.size() < 2) {
            throw invalid(node, "union 至少需要两个输入", "inputs");
        }
        List<PlanStep> inputs = new ArrayList<>();
        for (DraftEdge edge : edges) {
            DraftNode source = nodes.get(edge.sourceNodeId());
            if (source == null) {
                throw invalid(node, "输入连线指向的节点不存在", "inputs");
            }
            inputs.add(buildStep(source, nodes, incoming, inputsByDataset, planInputs, path));
        }
        return inputs;
    }

    private DraftNode requireInput(
            DraftNode node,
            Map<UUID, DraftNode> nodes,
            Map<UUID, List<DraftEdge>> incoming,
            String port
    ) {
        return incoming.getOrDefault(node.id(), List.of()).stream()
                .filter(edge -> port.equals(edge.targetPort()))
                .map(edge -> nodes.get(edge.sourceNodeId()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> invalid(node, "缺少输入端口连线: " + port, port));
    }

    private PlanStep step(DraftNode node, LogicalPlanNode planNode, List<PlanStep> inputs) {
        return new PlanStep(node.id().toString(), node.type(), planNode, inputs);
    }

    private Map<UUID, List<DraftEdge>> incoming(List<DraftEdge> edges) {
        Map<UUID, List<DraftEdge>> incoming = new LinkedHashMap<>();
        for (DraftEdge edge : edges) {
            incoming.computeIfAbsent(edge.targetNodeId(), ignored -> new ArrayList<>()).add(edge);
        }
        return incoming;
    }

    private String text(DraftNode node, String field) {
        JsonNode config = node.config();
        return config == null ? null : config.path(field).asText(null);
    }

    private List<String> strings(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        value.forEach(item -> {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private List<LogicalPlanNode.ColumnRename> renames(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<LogicalPlanNode.ColumnRename> renames = new ArrayList<>();
        value.forEach(item -> renames.add(new LogicalPlanNode.ColumnRename(
                item.path("from").asText(null),
                item.path("to").asText(null)
        )));
        return renames;
    }

    private List<LogicalPlanNode.JoinKey> joinKeys(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<LogicalPlanNode.JoinKey> keys = new ArrayList<>();
        value.forEach(item -> keys.add(new LogicalPlanNode.JoinKey(
                item.path("left").asText(null),
                item.path("right").asText(null)
        )));
        return keys;
    }

    private List<LogicalPlanNode.Aggregation> aggregations(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<LogicalPlanNode.Aggregation> aggregations = new ArrayList<>();
        value.forEach(item -> aggregations.add(new LogicalPlanNode.Aggregation(
                item.path("op").asText(null),
                item.path("field").asText(null),
                item.path("outputField").asText(null)
        )));
        return aggregations;
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private ApiException invalid(DraftNode node, String message, String field) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("nodeId", node.id().toString());
        if (field != null) {
            details.put("field", field);
        }
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PREVIEW_PLAN_INVALID", message, details);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
