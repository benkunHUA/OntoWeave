package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.catalog.CanonicalJson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class PipelineDefinitionHasher {

    private final ObjectMapper mapper;

    public PipelineDefinitionHasher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String normalizedHash(JsonNode definition, JsonNode outputs, JsonNode inputRefs) {
        ObjectNode normalized = mapper.createObjectNode();
        normalized.set("definition", normalizeDefinition(definition));
        normalized.set("outputs", sortedArray(outputs, "outputId"));
        normalized.set("inputRefs", sortedArray(inputRefs, "datasetKind", "datasetId"));
        return CanonicalJson.sha256(normalized);
    }

    private JsonNode normalizeDefinition(JsonNode definition) {
        JsonNode copy = definition == null ? mapper.createObjectNode() : definition.deepCopy();
        if (copy.isObject()) {
            JsonNode nodes = copy.get("nodes");
            if (nodes != null && nodes.isArray()) {
                List<JsonNode> normalizedNodes = new ArrayList<>();
                nodes.forEach(node -> {
                    JsonNode nodeCopy = node.deepCopy();
                    if (nodeCopy instanceof ObjectNode object) {
                        object.remove("position");
                    }
                    normalizedNodes.add(nodeCopy);
                });
                normalizedNodes.sort(Comparator.comparing(
                        node -> node.path("id").asText(""), Comparator.naturalOrder()
                ));
                ArrayNode array = mapper.createArrayNode();
                normalizedNodes.forEach(array::add);
                ((ObjectNode) copy).set("nodes", array);
            }
            JsonNode edges = copy.get("edges");
            if (edges != null && edges.isArray()) {
                List<JsonNode> normalizedEdges = new ArrayList<>();
                edges.forEach(edge -> normalizedEdges.add(edge.deepCopy()));
                normalizedEdges.sort(Comparator.comparing(
                        edge -> edge.path("id").asText(""), Comparator.naturalOrder()
                ));
                ArrayNode array = mapper.createArrayNode();
                normalizedEdges.forEach(array::add);
                ((ObjectNode) copy).set("edges", array);
            }
        }
        return copy;
    }

    private JsonNode sortedArray(JsonNode value, String... keys) {
        List<JsonNode> items = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> items.add(item.deepCopy()));
        }
        items.sort((left, right) -> {
            for (String key : keys) {
                int compared = left.path(key).asText("")
                        .compareTo(right.path(key).asText(""));
                if (compared != 0) {
                    return compared;
                }
            }
            return CanonicalJson.sha256(left).compareTo(CanonicalJson.sha256(right));
        });
        ArrayNode array = mapper.createArrayNode();
        items.forEach(array::add);
        return array;
    }
}
