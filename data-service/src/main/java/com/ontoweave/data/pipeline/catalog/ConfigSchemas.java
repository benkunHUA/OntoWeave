package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.ontoweave.data.common.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConfigSchemas {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final ConcurrentMap<String, JsonSchema> COMPILED = new ConcurrentHashMap<>();

    private ConfigSchemas() {
    }

    public static ObjectNode schemaFor(String nodeType) {
        return ConfigSchemaDefinitions.forType(nodeType);
    }

    public static void validate(NodeDefinition definition, JsonNode config) {
        validate(definition.type(), definition.version(), definition.configSchema(), config);
    }

    public static void validate(String nodeType, JsonNode config) {
        if (!ConfigSchemaDefinitions.contains(nodeType)) {
            throw unknownType(nodeType);
        }
        validate(nodeType, CURRENT_SCHEMA_VERSION, ConfigSchemaDefinitions.forType(nodeType), config);
    }

    private static void validate(String nodeType, int version, ObjectNode schemaNode, JsonNode config) {
        if (config == null || !config.isObject()) {
            throw invalid(nodeType, List.of(Map.of("path", "$", "message", "配置必须是 JSON 对象")));
        }
        String cacheKey = nodeType + "@" + version + ":" + CanonicalJson.sha256(schemaNode);
        JsonSchema schema = COMPILED.computeIfAbsent(
                cacheKey,
                ignored -> FACTORY.getSchema(schemaNode)
        );
        Set<ValidationMessage> violations = schema.validate(config);
        if (violations.isEmpty()) {
            return;
        }
        List<Map<String, Object>> errors = violations.stream()
                .map(message -> Map.<String, Object>of(
                        "path", message.getInstanceLocation().toString(),
                        "message", message.getMessage()
                ))
                .toList();
        throw invalid(nodeType, errors);
    }

    private static ApiException unknownType(String nodeType) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "NODE_TYPE_UNKNOWN",
                "未知的节点类型: " + nodeType,
                Map.of("type", nodeType == null ? "" : nodeType)
        );
    }

    private static ApiException invalid(String nodeType, List<Map<String, Object>> errors) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("nodeType", nodeType);
        details.put("errors", errors);
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "CONFIG_INVALID",
                "节点配置结构不合法",
                details
        );
    }
}
