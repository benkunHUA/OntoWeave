package com.ontoweave.data.connection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.config.ConnectionMaterialValidator;
import com.ontoweave.data.connection.config.MysqlConfig;
import com.ontoweave.data.connection.domain.ConnectionKind;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.domain.DataConnectionSecretEntity;
import com.ontoweave.data.connection.repository.DataConnectionSecretRepository;
import com.ontoweave.data.security.SecretCipher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ConnectionMaterialService {

    private final DataConnectionService connections;
    private final DataConnectionSecretRepository secrets;
    private final ConnectionMaterialValidator validator;
    private final SecretCipher secretCipher;

    public ConnectionMaterialService(
            DataConnectionService connections,
            DataConnectionSecretRepository secrets,
            ConnectionMaterialValidator validator,
            SecretCipher secretCipher
    ) {
        this.connections = connections;
        this.secrets = secrets;
        this.validator = validator;
        this.secretCipher = secretCipher;
    }

    public MysqlConnectionMaterial loadMysql(UUID id, UUID ownerId) {
        DataConnectionEntity connection = connections.required(id, ownerId);
        return material(connection);
    }

    public MysqlConnectionMaterial loadMysqlInternal(UUID id) {
        return material(connections.load(id));
    }

    private MysqlConnectionMaterial material(DataConnectionEntity connection) {
        if (connection.getKind() != ConnectionKind.MYSQL) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_CONNECTION_KIND",
                    "暂不支持的连接类型",
                    Map.of("kind", connection.getKind().value())
            );
        }
        JsonNode configNode = connection.getConfig();
        if (configNode == null || configNode.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONFIG_INCOMPLETE", "连接配置尚未填写");
        }
        MysqlConfig config = MysqlConfig.from(validator.validateConfig(connection.getKind(), configNode));
        DataConnectionSecretEntity secret = secrets.findById(connection.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SECRET_MISSING",
                        "连接密码尚未填写"
                ));
        JsonNode secretNode = secretCipher.decrypt(
                connection.getId(),
                connection.getKind().value(),
                secret.getCiphertext(),
                secret.getKeyVersion()
        );
        return new MysqlConnectionMaterial(connection, config, secretNode.path("password").asText());
    }

    public record MysqlConnectionMaterial(
            DataConnectionEntity connection,
            MysqlConfig config,
            String password
    ) {
    }
}
