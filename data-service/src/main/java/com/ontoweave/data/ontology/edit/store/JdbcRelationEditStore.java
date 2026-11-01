package com.ontoweave.data.ontology.edit.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRelationEditStore {

    private final JdbcTemplate jdbc;

    public JdbcRelationEditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RelationEditRow> findEditForUpdate(
            UUID ownerId,
            UUID relationId,
            UUID fromTypeId,
            String fromPrimaryKey,
            UUID toTypeId,
            String toPrimaryKey
    ) {
        return find(ownerId, relationId, fromTypeId, fromPrimaryKey, toTypeId, toPrimaryKey, true);
    }

    public Optional<RelationEditRow> findEdit(
            UUID ownerId,
            UUID relationId,
            UUID fromTypeId,
            String fromPrimaryKey,
            UUID toTypeId,
            String toPrimaryKey
    ) {
        return find(ownerId, relationId, fromTypeId, fromPrimaryKey, toTypeId, toPrimaryKey, false);
    }

    private Optional<RelationEditRow> find(
            UUID ownerId,
            UUID relationId,
            UUID fromTypeId,
            String fromPrimaryKey,
            UUID toTypeId,
            String toPrimaryKey,
            boolean forUpdate
    ) {
        List<RelationEditRow> rows = jdbc.query("""
                        SELECT state, version, stale_at
                        FROM data.ontology_relation_edit
                        WHERE owner_id = ? AND relation_id = ?
                          AND from_object_type_id = ? AND from_primary_key = ?
                          AND to_object_type_id = ? AND to_primary_key = ?
                        """ + (forUpdate ? " FOR UPDATE" : ""),
                (resultSet, rowNumber) -> new RelationEditRow(
                        resultSet.getString("state"),
                        resultSet.getLong("version"),
                        resultSet.getTimestamp("stale_at") != null
                ),
                ownerId, relationId, fromTypeId, fromPrimaryKey, toTypeId, toPrimaryKey
        );
        return rows.stream().findFirst();
    }

    public void upsert(
            UUID ownerId,
            UUID relationId,
            UUID fromTypeId,
            String fromPrimaryKey,
            UUID toTypeId,
            String toPrimaryKey,
            String state,
            UUID executionId,
            UUID updatedBy
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_relation_edit
                    (owner_id, relation_id, from_object_type_id, from_primary_key,
                     to_object_type_id, to_primary_key, state,
                     last_execution_id, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, relation_id, from_object_type_id, from_primary_key,
                             to_object_type_id, to_primary_key)
                DO UPDATE SET state = EXCLUDED.state,
                              last_execution_id = EXCLUDED.last_execution_id,
                              stale_at = NULL, stale_reason = NULL,
                              version = data.ontology_relation_edit.version + 1,
                              updated_at = now()
                """,
                ownerId, relationId, fromTypeId, fromPrimaryKey, toTypeId,
                toPrimaryKey, state, executionId, updatedBy
        );
    }

    public record RelationEditRow(String state, long version, boolean stale) {
    }
}
