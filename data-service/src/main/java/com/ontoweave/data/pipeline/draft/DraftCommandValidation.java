package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.catalog.ConfigSchemas;
import com.ontoweave.data.pipeline.catalog.NodeDefinition;
import com.ontoweave.data.pipeline.catalog.NodePort;
import com.ontoweave.data.pipeline.catalog.NodePortRole;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static com.ontoweave.data.pipeline.draft.DraftConstants.CODE_CONFIG_INVALID;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_TYPE;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_X;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_Y;

final class DraftCommandValidation {

    private DraftCommandValidation() {
    }

    static ObjectNode validateConfig(PipelineNodeCatalog catalog, String type, JsonNode config) {
        if (config == null || !config.isObject()) {
            throw invalid("节点 config 必须是 JSON 对象");
        }
        NodeDefinition definition = catalogDefinition(catalog, type);
        try {
            ConfigSchemas.validate(definition, config);
        } catch (ApiException error) {
            throw invalid("节点配置结构不合法: " + error.getMessage());
        }
        return ((ObjectNode) config).deepCopy();
    }

    static ObjectNode requirePosition(JsonNode position) {
        if (position == null || !position.isObject()) {
            throw invalid("position 必须是包含数值 x/y 的对象");
        }
        if (!position.path(FIELD_X).isNumber() || !position.path(FIELD_Y).isNumber()) {
            throw invalid("position.x 与 position.y 必须是数值");
        }
        return ((ObjectNode) position).deepCopy();
    }

    static void requireOutputNode(ObjectNode node) {
        if (!"output".equals(node.path(FIELD_TYPE).asText())) {
            throw invalid("set_output 只能作用于 output 节点");
        }
    }

    static void requirePort(
            PipelineNodeCatalog catalog,
            ObjectNode node,
            String portId,
            NodePortRole role
    ) {
        String type = requireNodeType(catalog, node.path(FIELD_TYPE).asText());
        String requiredPort = requireText(portId, "port");
        List<NodePort> ports = catalogDefinition(catalog, type).ports();
        boolean found = ports.stream()
                .anyMatch(port -> port.id().equals(requiredPort) && port.role() == role);
        if (!found) {
            throw invalid("端口不合法: " + type + "." + portId);
        }
    }

    static String requireNodeType(PipelineNodeCatalog catalog, String type) {
        if (type == null || !catalog.types().contains(type)) {
            throw invalid("未知的节点类型: " + type);
        }
        return type;
    }

    static void requireId(UUID id, String field) {
        if (id == null) {
            throw invalid(field + " 不能为空");
        }
    }

    static String requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return text.trim();
    }

    static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_CONFIG_INVALID, message);
    }

    private static NodeDefinition catalogDefinition(PipelineNodeCatalog catalog, String type) {
        return catalog.nodes().stream()
                .filter(node -> node.type().equals(type))
                .findFirst()
                .orElseThrow(() -> invalid("未知的节点类型: " + type));
    }
}
