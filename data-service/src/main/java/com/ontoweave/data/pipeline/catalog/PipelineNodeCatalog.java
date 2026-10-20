package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
public record PipelineNodeCatalog(List<NodeDefinition> nodes) {

    private static final int NODE_VERSION = 1;
    private static final String CATEGORY_SOURCE = "source";
    private static final String CATEGORY_TRANSFORM = "transform";
    private static final String CATEGORY_OUTPUT = "output";
    private static final String DATA_TYPE_TABLE = "table";
    private static final ObjectMapper JSON = new ObjectMapper();

    public PipelineNodeCatalog {
        nodes = List.copyOf(nodes);
    }

    public PipelineNodeCatalog() {
        this(defaultCatalog().nodes());
    }

    public NodeDefinition require(String type) {
        return nodes.stream()
                .filter(node -> node.type().equals(type))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "NODE_TYPE_UNKNOWN",
                        "未知的节点类型: " + type,
                        Map.of("type", type == null ? "" : type)
                ));
    }

    public List<String> types() {
        return nodes.stream().map(NodeDefinition::type).toList();
    }

    public String catalogVersion() {
        ArrayNode canonicalNodes = JSON.createArrayNode();
        nodes.stream()
                .sorted((left, right) -> left.type().compareTo(right.type()))
                .map(node -> canonicalize(JSON.valueToTree(node)))
                .forEach(canonicalNodes::add);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(JSON.writeValueAsBytes(canonicalNodes));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Unable to compute pipeline node catalog version", error);
        }
    }

    public NodeCatalogResponse response() {
        return new NodeCatalogResponse(catalogVersion(), nodes);
    }

    public static PipelineNodeCatalog defaultCatalog() {
        return new PipelineNodeCatalog(List.of(
                node("input", "数据输入", CATEGORY_SOURCE, List.of(outputPort())),
                node("filter", "数据筛选", CATEGORY_TRANSFORM, List.of(inputPort(), outputPort())),
                node("select_columns", "选择列", CATEGORY_TRANSFORM, List.of(inputPort(), outputPort())),
                node("rename_columns", "重命名列", CATEGORY_TRANSFORM, List.of(inputPort(), outputPort())),
                node("derive_column", "派生列", CATEGORY_TRANSFORM, List.of(inputPort(), outputPort())),
                node("deduplicate", "数据去重", CATEGORY_TRANSFORM, List.of(inputPort(), outputPort())),
                node("join", "表连接", CATEGORY_TRANSFORM, List.of(
                        new NodePort("left", NodePortRole.INPUT, DATA_TYPE_TABLE, true, false),
                        new NodePort("right", NodePortRole.INPUT, DATA_TYPE_TABLE, true, false),
                        outputPort()
                )),
                node("union", "表合并", CATEGORY_TRANSFORM, List.of(
                        new NodePort("inputs", NodePortRole.INPUT, DATA_TYPE_TABLE, true, true),
                        outputPort()
                )),
                node("aggregate", "数据聚合", CATEGORY_TRANSFORM, List.of(inputPort(), outputPort())),
                node("output", "数据输出", CATEGORY_OUTPUT, List.of(inputPort()))
        ));
    }

    private static NodeDefinition node(
            String type,
            String label,
            String category,
            List<NodePort> ports
    ) {
        return new NodeDefinition(
                type,
                NODE_VERSION,
                label,
                category,
                ports,
                ConfigSchemas.schemaFor(type),
                UiSchemas.forType(type)
        );
    }

    private static NodePort inputPort() {
        return new NodePort("in", NodePortRole.INPUT, DATA_TYPE_TABLE, true, false);
    }

    private static NodePort outputPort() {
        return new NodePort("out", NodePortRole.OUTPUT, DATA_TYPE_TABLE, false, false);
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JSON.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> sorted.set(name, canonicalize(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JSON.createArrayNode();
            node.forEach(element -> sorted.add(canonicalize(element)));
            return sorted;
        }
        return node;
    }
}
