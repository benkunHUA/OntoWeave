package com.ontoweave.data.connection.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.domain.ConnectionKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MysqlSecretValidator implements ConnectionSecretValidator {

    @Override
    public ConnectionKind kind() {
        return ConnectionKind.MYSQL;
    }

    @Override
    public JsonNode validate(JsonNode secret) {
        if (secret == null || !secret.isObject()) {
            throw invalid("secret 必须是对象");
        }
        JsonNode password = secret.get("password");
        if (password == null || !password.isTextual() || password.asText().isBlank()) {
            throw invalid("MySQL 密码不能为空");
        }
        return secret;
    }

    private ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                Map.of("fields", Map.of("password", message))
        );
    }
}
