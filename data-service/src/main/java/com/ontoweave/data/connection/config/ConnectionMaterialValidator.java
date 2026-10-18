package com.ontoweave.data.connection.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.domain.ConnectionKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ConnectionMaterialValidator {

    private final Map<ConnectionKind, ConnectionConfigValidator> configValidators = new EnumMap<>(ConnectionKind.class);
    private final Map<ConnectionKind, ConnectionSecretValidator> secretValidators = new EnumMap<>(ConnectionKind.class);

    public ConnectionMaterialValidator(
            List<ConnectionConfigValidator> configValidators,
            List<ConnectionSecretValidator> secretValidators
    ) {
        configValidators.forEach(validator -> this.configValidators.put(validator.kind(), validator));
        secretValidators.forEach(validator -> this.secretValidators.put(validator.kind(), validator));
    }

    public JsonNode validateConfig(ConnectionKind kind, JsonNode config) {
        ConnectionConfigValidator validator = configValidators.get(kind);
        if (validator == null) {
            throw unsupported(kind);
        }
        return validator.validate(config);
    }

    public JsonNode validateSecret(ConnectionKind kind, JsonNode secret) {
        ConnectionSecretValidator validator = secretValidators.get(kind);
        if (validator == null) {
            throw unsupported(kind);
        }
        return validator.validate(secret);
    }

    private ApiException unsupported(ConnectionKind kind) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_CONNECTION_KIND",
                "暂不支持的连接类型",
                Map.of("kind", kind.value())
        );
    }
}
