package com.ontoweave.data.ontology.edit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.ontology.query.RelationQueryFixtureSupport;
import com.ontoweave.data.ontology.query.RelationQueryFixtureSupport.M2mFixture;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class EditOverlayFixtureSupport extends RelationQueryFixtureSupport {

    protected void insertObjectEdit(
            ObjectFixture fixture,
            String primaryKey,
            String origin,
            String state,
            String title,
            Map<UUID, Object> patch
    ) {
        insertObjectEdit(fixture, primaryKey, origin, state, title, patch, Set.of());
    }

    protected void insertObjectEdit(
            ObjectFixture fixture,
            String primaryKey,
            String origin,
            String state,
            String title,
            Map<UUID, Object> patch,
            Set<UUID> cleared
    ) {
        ObjectNode properties = mapper.createObjectNode();
        patch.forEach((attributeId, value) ->
                properties.set(attributeId.toString(), mapper.valueToTree(value)));
        String datatype = fixture.primaryKeyDatatype();
        BigDecimal number = datatype.equals("integer") || datatype.equals("decimal")
                ? new BigDecimal(primaryKey)
                : null;
        LocalDate date = datatype.equals("date") ? LocalDate.parse(primaryKey) : null;
        Timestamp timestamp = datatype.equals("timestamp") || datatype.equals("timestamptz")
                ? Timestamp.from(Instant.parse(primaryKey))
                : null;
        boolean userOrigin = "USER".equals(origin);
        jdbc.update("""
                INSERT INTO data.ontology_object_edit
                    (owner_id, object_type_id, primary_key_value, origin, state,
                     title_edited, title_value, properties_patch, properties_cleared,
                     primary_key_num, primary_key_date, primary_key_ts,
                     source_binding_id, source_dataset_id, source_primary_key_attr_id,
                     updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS text[]),
                        ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, object_type_id, primary_key_value)
                DO UPDATE SET origin = EXCLUDED.origin, state = EXCLUDED.state,
                              title_edited = EXCLUDED.title_edited,
                              title_value = EXCLUDED.title_value,
                              properties_patch = EXCLUDED.properties_patch,
                              properties_cleared = EXCLUDED.properties_cleared,
                              stale_at = NULL, stale_reason = NULL,
                              version = data.ontology_object_edit.version + 1,
                              updated_at = now()
                """,
                fixture.ownerId(), fixture.objectTypeId(), primaryKey, origin, state,
                title != null || userOrigin, title, properties.toString(), arrayLiteral(cleared),
                number, date, timestamp,
                userOrigin ? null : fixture.bindingId(),
                userOrigin ? null : UUID.randomUUID(),
                userOrigin ? null : fixture.primaryKeyAttributeId(),
                UUID.randomUUID()
        );
    }

    protected void insertValueEdit(
            ObjectFixture fixture,
            String primaryKey,
            UUID attributeId,
            Object value
    ) {
        String datatype = fixture.attribute(attributeId).datatype();
        String text = null;
        BigDecimal number = null;
        LocalDate date = null;
        Timestamp timestamp = null;
        Boolean bool = null;
        if (value != null) {
            switch (datatype) {
                case "integer", "decimal" -> number = new BigDecimal(value.toString());
                case "date" -> date = LocalDate.parse(value.toString());
                case "timestamp", "timestamptz" -> timestamp = Timestamp.from(
                        Instant.parse(value.toString())
                );
                case "boolean" -> bool = (Boolean) value;
                default -> text = value.toString();
            }
        }
        jdbc.update("""
                INSERT INTO data.ontology_object_value_edit
                    (owner_id, object_type_id, primary_key_value, attribute_id, value_state,
                     text_value, num_value, date_value, ts_value, bool_value, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, object_type_id, primary_key_value, attribute_id)
                DO UPDATE SET value_state = EXCLUDED.value_state,
                              text_value = EXCLUDED.text_value,
                              num_value = EXCLUDED.num_value,
                              date_value = EXCLUDED.date_value,
                              ts_value = EXCLUDED.ts_value,
                              bool_value = EXCLUDED.bool_value,
                              stale_at = NULL, stale_reason = NULL,
                              updated_at = now()
                """,
                fixture.ownerId(), fixture.objectTypeId(), primaryKey, attributeId,
                value == null ? "CLEAR" : "SET", text, number, date, timestamp, bool,
                UUID.randomUUID()
        );
    }

    protected void insertUserObject(
            ObjectFixture fixture,
            String primaryKey,
            String title,
            Map<UUID, Object> values
    ) {
        Map<UUID, Object> patch = new LinkedHashMap<>(values);
        insertObjectEdit(fixture, primaryKey, "USER", "ACTIVE", title, patch);
        patch.forEach((attributeId, value) ->
                insertValueEdit(fixture, primaryKey, attributeId, value));
    }

    protected void insertRelationEdit(
            M2mFixture fixture,
            ObjectFixture source,
            ObjectFixture target,
            String fromPrimaryKey,
            String toPrimaryKey,
            String state
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_relation_edit
                    (owner_id, relation_id, from_object_type_id, from_primary_key,
                     to_object_type_id, to_primary_key, state, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, relation_id, from_object_type_id, from_primary_key,
                             to_object_type_id, to_primary_key)
                DO UPDATE SET state = EXCLUDED.state,
                              stale_at = NULL, stale_reason = NULL,
                              version = data.ontology_relation_edit.version + 1,
                              updated_at = now()
                """,
                source.ownerId(), fixture.relationId(), source.objectTypeId(), fromPrimaryKey,
                target.objectTypeId(), toPrimaryKey, state, UUID.randomUUID()
        );
    }

    protected void markObjectStale(ObjectFixture fixture, String primaryKey, String reason) {
        jdbc.update("""
                UPDATE data.ontology_object_edit
                SET stale_at = now(), stale_reason = ?
                WHERE owner_id = ? AND object_type_id = ? AND primary_key_value = ?
                """, reason, fixture.ownerId(), fixture.objectTypeId(), primaryKey);
    }

    private String arrayLiteral(Set<UUID> values) {
        if (values.isEmpty()) {
            return "{}";
        }
        return "{\"" + values.stream().map(UUID::toString)
                .collect(java.util.stream.Collectors.joining("\",\"")) + "\"}";
    }
}
