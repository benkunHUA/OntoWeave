package com.ontoweave.data.ontology.edit.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.model.EditSetResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcEditAuditStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcEditAuditStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<EditSetResult> findApplied(UUID ownerId, String idempotencyKey) {
        List<String> rows = jdbc.query("""
                        SELECT diff
                        FROM data.ontology_edit_execution
                        WHERE owner_id = ? AND idempotency_key = ? AND status = 'APPLIED'
                        """,
                (resultSet, rowNumber) -> resultSet.getString("diff"),
                ownerId,
                idempotencyKey
        );
        return rows.stream().findFirst().map(this::result);
    }

    public void insertExecution(
            EditSetRequest request,
            JsonNode baseVersions,
            JsonNode result
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_edit_execution
                    (id, owner_id, conversation_id, graph_id, action_type_id,
                     action_snapshot, action_definition_hash, parameters,
                     status, idempotency_key, base_versions, diff, applied_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb),
                        'APPLIED', ?, CAST(? AS jsonb), CAST(? AS jsonb), now())
                """,
                request.executionId(), request.ownerId(), request.conversationId(),
                request.graphId(), request.actionTypeId(),
                node(request.actionSnapshot()).toString(),
                request.actionDefinitionHash() == null ? "" : request.actionDefinitionHash(),
                node(request.parameters()).toString(), request.idempotencyKey(),
                baseVersions.toString(), result.toString()
        );
    }

    public void insertChange(
            UUID executionId,
            int seq,
            String changeKind,
            UUID objectTypeId,
            String primaryKey,
            UUID attributeId,
            UUID relationId,
            UUID fromObjectTypeId,
            String fromPrimaryKey,
            UUID toObjectTypeId,
            String toPrimaryKey,
            JsonNode before,
            JsonNode after
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_edit_change
                    (execution_id, seq, change_kind, object_type_id, primary_key_value,
                     attribute_id, relation_id, from_object_type_id, from_primary_key,
                     to_object_type_id, to_primary_key, before_value, after_value)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """,
                executionId, seq, changeKind, objectTypeId, primaryKey, attributeId,
                relationId, fromObjectTypeId, fromPrimaryKey, toObjectTypeId,
                toPrimaryKey, json(before), json(after)
        );
    }

    private EditSetResult result(String value) {
        try {
            return mapper.treeToValue(nodeString(value), EditSetResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("invalid edit execution result", exception);
        }
    }

    private JsonNode node(JsonNode node) {
        return node == null ? mapper.createObjectNode() : node;
    }

    private JsonNode nodeString(String value) {
        try {
            return value == null ? mapper.createObjectNode() : mapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("invalid edit execution JSON", exception);
        }
    }

    private String json(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.toString();
    }
}
