package com.ontoweave.data.connection.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.api.dto.CreateDataConnectionRequest;
import com.ontoweave.data.connection.api.dto.DataConnectionPageResponse;
import com.ontoweave.data.connection.api.dto.DataConnectionResponse;
import com.ontoweave.data.connection.api.dto.UpdateDataConnectionRequest;
import com.ontoweave.data.connection.config.ConnectionMaterialValidator;
import com.ontoweave.data.connection.domain.ConnectionKind;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.domain.DataConnectionSecretEntity;
import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.connection.repository.DataConnectionSecretRepository;
import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.security.EncryptedSecret;
import com.ontoweave.data.security.SecretCipher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DataConnectionService {

    private final DataConnectionRepository connections;
    private final DataConnectionSecretRepository secrets;
    private final ConnectionMaterialValidator validator;
    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;
    private final OutboxWriter outbox;

    public DataConnectionService(
            DataConnectionRepository connections,
            DataConnectionSecretRepository secrets,
            ConnectionMaterialValidator validator,
            SecretCipher secretCipher,
            ObjectMapper objectMapper,
            OutboxWriter outbox
    ) {
        this.connections = connections;
        this.secrets = secrets;
        this.validator = validator;
        this.secretCipher = secretCipher;
        this.objectMapper = objectMapper;
        this.outbox = outbox;
    }

    @Transactional
    public DataConnectionResponse create(CreateDataConnectionRequest request) {
        String name = request.name().trim();
        ConnectionKind kind = ConnectionKind.from(request.kind());
        JsonNode config = request.config() == null
                ? objectMapper.createObjectNode()
                : validator.validateConfig(kind, request.config());
        JsonNode secret = request.secret() == null
                ? null
                : validator.validateSecret(kind, request.secret());

        var existing = connections.findByConversationId(request.conversationId());
        if (existing.isPresent()) {
            DataConnectionEntity connection = existing.get();
            if (!connection.getOwnerId().equals(request.ownerId())) {
                throw conflict("conversation_id 已被其他用户使用");
            }
            return response(connection);
        }
        if (connections.existsByOwnerIdAndName(request.ownerId(), name)) {
            throw conflict("连接名称已存在");
        }

        UUID id = UUID.randomUUID();
        DataConnectionEntity connection = new DataConnectionEntity(
                id,
                request.ownerId(),
                request.conversationId(),
                name,
                kind
        );
        connection.initializeConfig(config);
        DataConnectionEntity saved = connections.save(connection);

        if (secret != null) {
            EncryptedSecret encrypted = secretCipher.encrypt(id, kind.value(), secret);
            secrets.save(new DataConnectionSecretEntity(id, encrypted.ciphertext(), encrypted.keyVersion()));
        }
        return response(saved);
    }

    @Transactional(readOnly = true)
    public DataConnectionPageResponse list(UUID ownerId, int limit, int offset) {
        Page<DataConnectionEntity> page = connections.findByOwnerIdOrderByCreatedAtDesc(
                ownerId,
                PageRequest.of(offset / limit, limit)
        );
        List<DataConnectionResponse> items = page.getContent().stream().map(this::response).toList();
        return new DataConnectionPageResponse(items, page.getTotalElements(), limit, offset);
    }

    @Transactional(readOnly = true)
    public DataConnectionResponse get(UUID id, UUID ownerId) {
        return response(required(id, ownerId));
    }

    @Transactional
    public DataConnectionResponse update(
            UUID id,
            UUID ownerId,
            UpdateDataConnectionRequest request,
            UUID operationId
    ) {
        DataConnectionEntity connection = required(id, ownerId);
        boolean changed = false;
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw validation("连接名称不能为空");
            }
            if (!Objects.equals(name, connection.getName())
                    && connections.existsByOwnerIdAndName(connection.getOwnerId(), name)) {
                throw conflict("连接名称已存在");
            }
            connection.changeName(name);
            changed = true;
        }
        if (request.config() != null) {
            JsonNode config = validator.validateConfig(connection.getKind(), request.config());
            if (!Objects.equals(config, connection.getConfig())) {
                connection.changeConfig(config);
                changed = true;
            }
        }
        if (request.secret() != null) {
            JsonNode secret = validator.validateSecret(connection.getKind(), request.secret());
            EncryptedSecret encrypted = secretCipher.encrypt(id, connection.getKind().value(), secret);
            DataConnectionSecretEntity stored = secrets.findById(id)
                    .orElseGet(() -> new DataConnectionSecretEntity(
                            id,
                            encrypted.ciphertext(),
                            encrypted.keyVersion()
                    ));
            stored.replace(encrypted.ciphertext(), encrypted.keyVersion());
            secrets.save(stored);
            connection.changeSecret();
            changed = true;
        }
        DataConnectionEntity saved = connections.save(connection);
        if (changed) {
            writeUpdatedEvent(saved, operationId);
        }
        return response(saved);
    }

    private void writeUpdatedEvent(DataConnectionEntity connection, UUID operationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("connectionId", connection.getId());
        payload.put("conversationId", connection.getConversationId());
        payload.put("operationId", operationId);
        payload.put("summary", "连接配置已保存");
        outbox.write("data_connection", connection.getId(), "connection.updated", payload);
    }

    @Transactional(readOnly = true)
    public DataConnectionEntity required(UUID id, UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return connections.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> notFound(id));
    }

    @Transactional(readOnly = true)
    public DataConnectionEntity load(UUID id) {
        return connections.findById(id).orElseThrow(() -> notFound(id));
    }

    public DataConnectionResponse response(DataConnectionEntity connection) {
        return new DataConnectionResponse(
                connection.getId(),
                connection.getOwnerId(),
                connection.getConversationId(),
                connection.getName(),
                connection.getKind().value(),
                connection.getConfig(),
                connection.getConfigVersion(),
                connection.getStatus().name().toLowerCase(),
                connection.getLastTestStatus().name().toLowerCase(),
                connection.getLastTestAt(),
                connection.getLastTestError(),
                connection.getLastTestDetails(),
                secrets.existsById(connection.getId()),
                connection.getCreatedAt(),
                connection.getUpdatedAt()
        );
    }

    private ApiException notFound(UUID id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "数据连接不存在", Map.of("id", id));
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    private ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
