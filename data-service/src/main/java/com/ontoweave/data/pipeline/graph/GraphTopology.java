package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.catalog.ConfigSchemas;
import com.ontoweave.data.pipeline.catalog.NodePort;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class GraphTopology {

    private GraphTopology() {
    }

    static Map<UUID, List<DraftEdge>> incoming(List<DraftEdge> edges) {
        Map<UUID, List<DraftEdge>> incoming = new LinkedHashMap<>();
        for (DraftEdge edge : edges) {
            incoming.computeIfAbsent(edge.targetNodeId(), ignored -> new ArrayList<>()).add(edge);
        }
        return incoming;
    }

    static Set<UUID> cycleNodes(
            Map<UUID, DraftNode> nodes,
            List<DraftEdge> edges,
            List<PipelineIssue> issues
    ) {
        Set<UUID> cycle = new LinkedHashSet<>(nodes.keySet());
        cycle.removeAll(topological(nodes, edges));
        for (UUID nodeId : cycle) {
            issues.add(new PipelineIssue(
                    PipelineIssueCodes.CYCLE_DETECTED,
                    PipelineIssueCodes.PHASE_GRAPH,
                    true,
                    nodeId.toString(),
                    null,
                    null,
                    "流程中存在环",
                    "移除形成环的连线"
            ));
        }
        return cycle;
    }

    static List<UUID> processingOrder(Map<UUID, DraftNode> nodes, List<DraftEdge> edges) {
        List<UUID> order = topological(nodes, edges);
        Set<UUID> seen = new LinkedHashSet<>(order);
        for (UUID nodeId : nodes.keySet()) {
            if (seen.add(nodeId)) {
                order.add(nodeId);
            }
        }
        return order;
    }

    static void reportRequiredInputs(
            Map<UUID, DraftNode> nodes,
            List<DraftEdge> edges,
            PipelineNodeCatalog catalog,
            List<PipelineIssue> issues
    ) {
        Map<UUID, List<DraftEdge>> incoming = incoming(edges);
        for (DraftNode node : nodes.values()) {
            NodeDefinitionView definition = definition(catalog, node.type());
            if (definition == null) {
                continue;
            }
            if ("output".equals(node.type()) && incoming.getOrDefault(node.id(), List.of()).isEmpty()) {
                issues.add(issue(
                        PipelineIssueCodes.OUTPUT_NOT_CONNECTED,
                        PipelineIssueCodes.PHASE_GRAPH,
                        node.id().toString(),
                        "output",
                        "输出节点没有输入连线"
                ));
                continue;
            }
            for (NodePort port : definition.ports()) {
                if (port.role() != com.ontoweave.data.pipeline.catalog.NodePortRole.INPUT || !port.required()) {
                    continue;
                }
                long count = incoming.getOrDefault(node.id(), List.of()).stream()
                        .filter(edge -> edge.targetPort().equals(port.id()))
                        .count();
                long minimum = port.multiple() && "union".equals(node.type()) ? 2 : 1;
                if (count < minimum) {
                    issues.add(issue(
                            PipelineIssueCodes.INPUT_MISSING,
                            PipelineIssueCodes.PHASE_GRAPH,
                            node.id().toString(),
                            port.id(),
                            "必需输入端口缺少连线"
                    ));
                }
            }
        }
    }

    private static List<UUID> topological(Map<UUID, DraftNode> nodes, List<DraftEdge> edges) {
        Map<UUID, Integer> indegree = new HashMap<>();
        Map<UUID, List<UUID>> outgoing = new HashMap<>();
        nodes.keySet().forEach(nodeId -> indegree.put(nodeId, 0));
        for (DraftEdge edge : edges) {
            if (!indegree.containsKey(edge.sourceNodeId()) || !indegree.containsKey(edge.targetNodeId())) {
                continue;
            }
            outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>())
                    .add(edge.targetNodeId());
            indegree.merge(edge.targetNodeId(), 1, Integer::sum);
        }
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        nodes.keySet().stream().filter(nodeId -> indegree.get(nodeId) == 0).forEach(queue::add);
        List<UUID> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            UUID nodeId = queue.removeFirst();
            order.add(nodeId);
            for (UUID target : outgoing.getOrDefault(nodeId, List.of())) {
                int remaining = indegree.merge(target, -1, Integer::sum);
                if (remaining == 0) {
                    queue.addLast(target);
                }
            }
        }
        return order;
    }

    private static NodeDefinitionView definition(PipelineNodeCatalog catalog, String type) {
        return catalog.nodes().stream()
                .filter(definition -> definition.type().equals(type))
                .findFirst()
                .map(definition -> new NodeDefinitionView(definition.ports()))
                .orElse(null);
    }

    private static PipelineIssue issue(
            String code,
            String phase,
            String nodeId,
            String portId,
            String message
    ) {
        return new PipelineIssue(code, phase, true, nodeId, portId, null, message, null);
    }

    private record NodeDefinitionView(List<NodePort> ports) {
    }
}
