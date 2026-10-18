package com.ontoweave.data.connection.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.domain.ConnectionKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MysqlConfigValidator implements ConnectionConfigValidator {

    @Override
    public ConnectionKind kind() {
        return ConnectionKind.MYSQL;
    }

    @Override
    public JsonNode validate(JsonNode config) {
        if (config == null || !config.isObject()) {
            throw invalid("config 必须是对象", Map.of());
        }
        Map<String, Object> errors = new LinkedHashMap<>();
        requireText(config, "host", errors);
        requireText(config, "database", errors);
        requireText(config, "username", errors);
        int port = config.path("port").asInt(3306);
        if (port < 1 || port > 65535) {
            errors.put("port", "端口必须在 1-65535 之间");
        }
        if (!errors.isEmpty()) {
            throw invalid("MySQL 连接配置不完整", Map.of("fields", errors));
        }
        ObjectNode normalized = config.deepCopy();
        if (!normalized.has("port")) {
            normalized.put("port", 3306);
        }
        if (!normalized.has("useSsl")) {
            normalized.put("useSsl", false);
        }
        return normalized;
    }

    private void requireText(JsonNode config, String field, Map<String, Object> errors) {
        JsonNode value = config.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            errors.put(field, field + " 不能为空");
        }
    }

    private ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, details);
    }
}
