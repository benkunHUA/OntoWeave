package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.catalog.ConfigSchemas;
import com.ontoweave.data.pipeline.catalog.NodeDefinition;
import com.ontoweave.data.pipeline.catalog.NodePort;
import com.ontoweave.data.pipeline.catalog.NodePortRole;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class DraftStructureChecker {

    private final PipelineNodeCatalog catalog;

    DraftStructureChecker(PipelineNodeCatalog catalog) {
        this.catalog = catalog;
    }

    StructureResult check(PipelineDraftGraph graph, List<PipelineIssue> issues) {
        JsonNode definition = graph.definition();
        if (!definition.isObject()) {
            issues.add(issue(PipelineIssueCodes.CONFIG_INVALID, "草稿根必须是 JSON 对象"));
            return new StructureResult(Map.of(), List.of(), true);
        }
        if (definition.path("schemaVersion").asInt(-1) != 1) {
            issues.add(issue(PipelineIssueCodes.CONFIG_INVALID, "schemaVersion 必须为 1"));
        }
        if (!definition.path("nodes").isArray() || !definition.path("edges").isArray()) {
            issues.add(issue(PipelineIssueCodes.CONFIG_INVALID, "nodes 与 edges 必须是数组"));
            return new StructureResult(Map.of(), List.of(), true);
        }

        Map<UUID, DraftNode> nodes = new LinkedHashMap<>();
        for (JsonNode raw : definition.withArray("nodes")) {
            DraftNode node = node(raw, issues);
            if (node == null) {
                continue;
            }
            if (nodes.putIfAbsent(node.id(), node) != null) {
                issues.add(issue(
                        PipelineIssueCodes.CONFIG_INVALID,
                        node.id().toString(),
                        "节点 ID 重复"
                ));
            }
        }

        List<DraftEdge> edges = new ArrayList<>();
        Map<UUID, Integer> edgeIds = new HashMap<>();
        Map<PortKey, List<DraftEdge>> occupiedPorts = new LinkedHashMap<>();
        for (JsonNode raw : definition.withArray("edges")) {
            DraftEdge edge = edge(raw, nodes, issues);
            if (edge == null) {
                continue;
            }
            int previous = edgeIds.merge(edge.id(), 1, Integer::sum);
            if (previous > 1) {
                issues.add(issue(
                        PipelineIssueCodes.CONFIG_INVALID,
                        edge.id().toString(),
                        "连线 ID 重复"
                ));
            }
            edges.add(edge);
            occupiedPorts.computeIfAbsent(new PortKey(edge.targetNodeId(), edge.targetPort()), ignored -> new ArrayList<>())
                    .add(edge);
            occupiedPorts.computeIfAbsent(new PortKey(edge.sourceNodeId(), edge.sourcePort()), ignored -> new ArrayList<>())
                    .add(edge);
        }
        reportOccupiedPorts(nodes, occupiedPorts, issues);
        return new StructureResult(Map.copyOf(nodes), List.copyOf(edges), false);
    }

    private DraftNode node(JsonNode raw, List<PipelineIssue> issues) {
        if (!raw.isObject()) {
            issues.add(issue(PipelineIssueCodes.CONFIG_INVALID, "节点必须是 JSON 对象"));
            return null;
        }
        UUID id = uuid(raw.path("id").asText(null));
        if (id == null) {
            issues.add(issue(PipelineIssueCodes.CONFIG_INVALID, "节点 id 必须是 UUID 字符串"));
            return null;
        }
        String nodeId = id.toString();
        String type = raw.path("type").asText(null);
        if (type == null || !catalog.types().contains(type)) {
            issues.add(issue(PipelineIssueCodes.NODE_TYPE_UNKNOWN, nodeId, "未知的节点类型: " + type));
            return new DraftNode(id, type == null ? "" : type, raw.path("name").asText(null), null, null);
        }
        if (raw.path("name").asText("").isBlank()) {
            issues.add(issue(PipelineIssueCodes.CONFIG_INVALID, nodeId, "节点 name 不能为空"));
        }
        JsonNode config = raw.path("config");
        if (!config.isObject()) {
            issues.add(issue(PipelineIssueCodes.CONFIG_INVALID, nodeId, "节点 config 必须是对象"));
            config = null;
        } else {
            validateConfig(nodeId, type, config, issues);
        }
        JsonNode position = raw.path("position");
        if (!position.isObject()
                || !position.path("x").isNumber()
                || !position.path("y").isNumber()) {
            issues.add(issue(PipelineIssueCodes.CONFIG_INVALID, nodeId, "position 必须包含数值 x/y"));
        }
        return new DraftNode(
                id,
                type,
                raw.path("name").asText(null),
                config == null ? null : config.deepCopy(),
                position.isObject() ? position.deepCopy() : null
        );
    }

    private DraftEdge edge(
            JsonNode raw,
            Map<UUID, DraftNode> nodes,
            List<PipelineIssue> issues
    ) {
        if (!raw.isObject()) {
            issues.add(issue(PipelineIssueCodes.EDGE_PORT_INVALID, null, "连线必须是 JSON 对象"));
            return null;
        }
        UUID id = uuid(raw.path("id").asText(null));
        UUID source = uuid(raw.path("sourceNodeId").asText(null));
        UUID target = uuid(raw.path("targetNodeId").asText(null));
        String sourcePort = raw.path("sourcePort").asText(null);
        String targetPort = raw.path("targetPort").asText(null);
        if (id == null || source == null || target == null
                || sourcePort == null || sourcePort.isBlank()
                || targetPort == null || targetPort.isBlank()) {
            issues.add(issue(PipelineIssueCodes.EDGE_PORT_INVALID, null, "连线身份或端口字段不完整"));
            return null;
        }
        DraftNode sourceNode = nodes.get(source);
        DraftNode targetNode = nodes.get(target);
        if (sourceNode == null || targetNode == null) {
            issues.add(issue(PipelineIssueCodes.EDGE_PORT_INVALID, null, "连线引用了不存在的节点"));
            return null;
        }
        if (!hasPort(sourceNode.type(), sourcePort, NodePortRole.OUTPUT)) {
            issues.add(issue(
                    PipelineIssueCodes.EDGE_PORT_INVALID,
                    source.toString(),
                    sourcePort,
                    "源端口不存在或不是输出端口"
            ));
            return null;
        }
        if (!hasPort(targetNode.type(), targetPort, NodePortRole.INPUT)) {
            issues.add(issue(
                    PipelineIssueCodes.EDGE_PORT_INVALID,
                    target.toString(),
                    targetPort,
                    "目标端口不存在或不是输入端口"
            ));
            return null;
        }
        return new DraftEdge(id, source, sourcePort, target, targetPort);
    }

    private void reportOccupiedPorts(
            Map<UUID, DraftNode> nodes,
            Map<PortKey, List<DraftEdge>> occupied,
            List<PipelineIssue> issues
    ) {
        occupied.forEach((key, edges) -> {
            DraftNode node = nodes.get(key.nodeId());
            if (node == null || edges.size() < 2) {
                return;
            }
            Optional<NodePort> port = port(node.type(), key.portId());
            if (port.isPresent() && !port.get().multiple()) {
                issues.add(issue(
                        PipelineIssueCodes.PORT_OCCUPIED,
                        key.nodeId().toString(),
                        key.portId(),
                        "端口只能连接一条边"
                ));
            }
        });
    }

    private void validateConfig(String nodeId, String type, JsonNode config, List<PipelineIssue> issues) {
        NodeDefinition definition = catalog.nodes().stream()
                .filter(node -> node.type().equals(type))
                .findFirst()
                .orElse(null);
        if (definition == null) {
            return;
        }
        JsonNode schemaConfig = "output".equals(type) ? outputConfigView(config) : config;
        try {
            ConfigSchemas.validate(definition, schemaConfig);
        } catch (ApiException error) {
            String code = error.details().toString().contains("required")
                    ? PipelineIssueCodes.NODE_CONFIG_INCOMPLETE
                    : PipelineIssueCodes.CONFIG_INVALID;
            issues.add(issue(code, nodeId, error.getMessage()));
        }
    }

    private ObjectNode outputConfigView(JsonNode config) {
        ObjectNode view = (ObjectNode) config.deepCopy();
        view.remove("primaryKeyColumns");
        view.remove("expectations");
        return view;
    }

    private boolean hasPort(String type, String portId, NodePortRole role) {
        return port(type, portId).map(port -> port.role() == role).orElse(false);
    }

    private Optional<NodePort> port(String type, String portId) {
        return catalog.nodes().stream()
                .filter(node -> node.type().equals(type))
                .flatMap(node -> node.ports().stream())
                .filter(port -> port.id().equals(portId))
                .findFirst();
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private PipelineIssue issue(String code, String message) {
        return issue(code, null, null, message);
    }

    private PipelineIssue issue(String code, String nodeId, String message) {
        return issue(code, nodeId, null, message);
    }

    private PipelineIssue issue(String code, String nodeId, String portId, String message) {
        return new PipelineIssue(
                code,
                PipelineIssueCodes.PHASE_STRUCTURE,
                true,
                nodeId,
                portId,
                null,
                message,
                null
        );
    }

    record StructureResult(Map<UUID, DraftNode> nodes, List<DraftEdge> edges, boolean fatal) {
    }

    private record PortKey(UUID nodeId, String portId) {
    }
}
