package com.ontoweave.data.ontology.edit.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcObjectEditStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcObjectEditStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<ObjectBindingContext> findBinding(UUID ownerId, UUID objectTypeId) {
        List<ObjectBindingContext> rows = jdbc.query("""
                        SELECT b.id AS binding_id,
                               COALESCE(b.active_dataset_id, b.draft_dataset_id) AS dataset_id,
                               v.id AS version_id, v.mapping_snapshot
                        FROM data.ontology_object_binding b
                        LEFT JOIN data.ontology_object_version v
                          ON v.id = b.current_index_version_id
                        WHERE b.owner_id = ? AND b.object_type_id = ? AND b.status = 'ACTIVE'
                        """,
                (resultSet, rowNumber) -> new ObjectBindingContext(
                        resultSet.getObject("binding_id", UUID.class),
                        resultSet.getObject("dataset_id", UUID.class),
                        resultSet.getObject("version_id", UUID.class),
                        json(resultSet.getString("mapping_snapshot"))
                ),
                ownerId,
                objectTypeId
        );
        return rows.stream().findFirst();
    }

    public Optional<ObjectEditRow> findEdit(UUID ownerId, UUID objectTypeId, String primaryKey) {
        return findEdit(ownerId, objectTypeId, primaryKey, false);
    }

    public Optional<ObjectEditRow> findEditForUpdate(
            UUID ownerId,
            UUID objectTypeId,
            String primaryKey
    ) {
        return findEdit(ownerId, objectTypeId, primaryKey, true);
    }

    private Optional<ObjectEditRow> findEdit(
            UUID ownerId,
            UUID objectTypeId,
            String primaryKey,
            boolean forUpdate
    ) {
        String sql = """
                SELECT origin, state, title_edited, title_value, properties_patch,
                       properties_cleared, version, stale_at
                FROM data.ontology_object_edit
                WHERE owner_id = ? AND object_type_id = ? AND primary_key_value = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        List<ObjectEditRow> rows = jdbc.query(
                sql,
                (resultSet, rowNumber) -> new ObjectEditRow(
                        resultSet.getString("origin"),
                        resultSet.getString("state"),
                        resultSet.getBoolean("title_edited"),
                        resultSet.getString("title_value"),
                        json(resultSet.getString("properties_patch")),
                        resultSet.getString("properties_cleared"),
                        resultSet.getLong("version"),
                        resultSet.getTimestamp("stale_at") != null
                ),
                ownerId,
                objectTypeId,
                primaryKey
        );
        return rows.stream().findFirst();
    }

    public void upsertObject(
            UUID ownerId,
            UUID objectTypeId,
            String primaryKey,
            String origin,
            String state,
            boolean titleEdited,
            String titleValue,
            JsonNode propertiesPatch,
            String[] propertiesCleared,
            BigDecimal primaryKeyNum,
            LocalDate primaryKeyDate,
            Timestamp primaryKeyTs,
            UUID sourceBindingId,
            UUID sourceDatasetId,
            UUID sourcePrimaryKeyAttributeId,
            UUID sourceTitleKeyAttributeId,
            UUID executionId,
            UUID updatedBy
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_object_edit
                    (owner_id, object_type_id, primary_key_value, origin, state,
                     title_edited, title_value, properties_patch, properties_cleared,
                     primary_key_num, primary_key_date, primary_key_ts,
                     source_binding_id, source_dataset_id, source_primary_key_attr_id,
                     source_title_key_attr_id, last_execution_id, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS text[]),
                        ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, object_type_id, primary_key_value)
                DO UPDATE SET origin = EXCLUDED.origin, state = EXCLUDED.state,
                              title_edited = EXCLUDED.title_edited,
                              title_value = EXCLUDED.title_value,
                              properties_patch = EXCLUDED.properties_patch,
                              properties_cleared = EXCLUDED.properties_cleared,
                              primary_key_num = EXCLUDED.primary_key_num,
                              primary_key_date = EXCLUDED.primary_key_date,
                              primary_key_ts = EXCLUDED.primary_key_ts,
                              source_binding_id = EXCLUDED.source_binding_id,
                              source_dataset_id = EXCLUDED.source_dataset_id,
                              source_primary_key_attr_id = EXCLUDED.source_primary_key_attr_id,
                              source_title_key_attr_id = EXCLUDED.source_title_key_attr_id,
                              last_execution_id = EXCLUDED.last_execution_id,
                              stale_at = NULL, stale_reason = NULL,
                              version = data.ontology_object_edit.version + 1,
                              updated_at = now()
                """,
                ownerId, objectTypeId, primaryKey, origin, state, titleEdited, titleValue,
                propertiesPatch == null ? "{}" : propertiesPatch.toString(),
                arrayLiteral(propertiesCleared), primaryKeyNum, primaryKeyDate, primaryKeyTs,
                sourceBindingId, sourceDatasetId, sourcePrimaryKeyAttributeId,
                sourceTitleKeyAttributeId, executionId, updatedBy
        );
    }

    public void upsertValue(
            UUID ownerId,
            UUID objectTypeId,
            String primaryKey,
            UUID attributeId,
            String datatype,
            JsonNode value,
            UUID executionId,
            UUID updatedBy
    ) {
        ValueColumns columns = columns(datatype, value);
        jdbc.update("""
                INSERT INTO data.ontology_object_value_edit
                    (owner_id, object_type_id, primary_key_value, attribute_id, value_state,
                     text_value, num_value, date_value, ts_value, bool_value,
                     last_execution_id, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, object_type_id, primary_key_value, attribute_id)
                DO UPDATE SET value_state = EXCLUDED.value_state,
                              text_value = EXCLUDED.text_value,
                              num_value = EXCLUDED.num_value,
                              date_value = EXCLUDED.date_value,
                              ts_value = EXCLUDED.ts_value,
                              bool_value = EXCLUDED.bool_value,
                              last_execution_id = EXCLUDED.last_execution_id,
                              stale_at = NULL, stale_reason = NULL,
                              updated_at = now()
                """,
                ownerId, objectTypeId, primaryKey, attributeId, columns.state(),
                columns.text(), columns.number(), columns.date(), columns.timestamp(),
                columns.bool(), executionId, updatedBy
        );
    }

    public void deleteValues(UUID ownerId, UUID objectTypeId, String primaryKey) {
        jdbc.update("""
                DELETE FROM data.ontology_object_value_edit
                WHERE owner_id = ? AND object_type_id = ? AND primary_key_value = ?
                """, ownerId, objectTypeId, primaryKey);
    }

    public void deleteRelationsForObject(UUID ownerId, UUID objectTypeId, String primaryKey) {
        jdbc.update("""
                DELETE FROM data.ontology_relation_edit
                WHERE owner_id = ?
                  AND ((from_object_type_id = ? AND from_primary_key = ?)
                    OR (to_object_type_id = ? AND to_primary_key = ?))
                """, ownerId, objectTypeId, primaryKey, objectTypeId, primaryKey);
    }

    private ValueColumns columns(String datatype, JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return new ValueColumns("CLEAR", null, null, null, null, null);
        }
        return switch (datatype == null ? "text" : datatype) {
            case "integer", "decimal" -> new ValueColumns(
                    "SET", null, value.decimalValue(), null, null, null
            );
            case "date" -> new ValueColumns(
                    "SET", null, null, LocalDate.parse(value.asText()), null, null
            );
            case "timestamp", "timestamptz" -> new ValueColumns(
                    "SET", null, null, null,
                    Timestamp.from(Instant.parse(value.asText())), null
            );
            case "boolean" -> new ValueColumns(
                    "SET", null, null, null, null, value.booleanValue()
            );
            default -> new ValueColumns("SET", value.asText(), null, null, null, null);
        };
    }

    private String arrayLiteral(String[] values) {
        if (values == null || values.length == 0) {
            return "{}";
        }
        return "{\"" + String.join("\",\"", values) + "\"}";
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("invalid object edit JSON", exception);
        }
    }

    public record ObjectBindingContext(
            UUID bindingId,
            UUID datasetId,
            UUID versionId,
            JsonNode mappingSnapshot
    ) {
    }

    public record ObjectEditRow(
            String origin,
            String state,
            boolean titleEdited,
            String titleValue,
            JsonNode propertiesPatch,
            String propertiesCleared,
            long version,
            boolean stale
    ) {
    }

    private record ValueColumns(
            String state,
            String text,
            BigDecimal number,
            LocalDate date,
            Timestamp timestamp,
            Boolean bool
    ) {
    }
}
