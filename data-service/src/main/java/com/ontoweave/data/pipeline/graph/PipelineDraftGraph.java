package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PipelineDraftGraph {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode definition;
    private final List<DraftNode> nodes;
    private final List<DraftEdge> edges;

    public PipelineDraftGraph(JsonNode definition) {
        this.definition = definition == null ? JSON.nullNode() : definition.deepCopy();
        this.nodes = parseNodes(this.definition.path("nodes"));
        this.edges = parseEdges(this.definition.path("edges"));
    }

    public static PipelineDraftGraph from(JsonNode definition) {
        return new PipelineDraftGraph(definition);
    }

    public JsonNode definition() {
        return definition.deepCopy();
    }

    public List<DraftNode> nodes() {
        return List.copyOf(nodes);
    }

    public List<DraftEdge> edges() {
        return List.copyOf(edges);
    }

    private List<DraftNode> parseNodes(JsonNode nodeArray) {
        if (!nodeArray.isArray()) {
            return List.of();
        }
        List<DraftNode> parsed = new ArrayList<>();
        for (JsonNode node : nodeArray) {
            UUID id = uuid(node.path("id").asText(null));
            String type = node.path("type").asText(null);
            if (id == null || type == null) {
                continue;
            }
            parsed.add(new DraftNode(
                    id,
                    type,
                    node.path("name").asText(null),
                    node.path("config").isObject() ? node.path("config").deepCopy() : null,
                    node.path("position").isObject() ? node.path("position").deepCopy() : null
            ));
        }
        return List.copyOf(parsed);
    }

    private List<DraftEdge> parseEdges(JsonNode edgeArray) {
        if (!edgeArray.isArray()) {
            return List.of();
        }
        List<DraftEdge> parsed = new ArrayList<>();
        for (JsonNode edge : edgeArray) {
            UUID id = uuid(edge.path("id").asText(null));
            UUID source = uuid(edge.path("sourceNodeId").asText(null));
            UUID target = uuid(edge.path("targetNodeId").asText(null));
            if (id == null || source == null || target == null) {
                continue;
            }
            parsed.add(new DraftEdge(
                    id,
                    source,
                    edge.path("sourcePort").asText(null),
                    target,
                    edge.path("targetPort").asText(null)
            ));
        }
        return List.copyOf(parsed);
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

}
