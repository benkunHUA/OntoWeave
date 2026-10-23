package com.ontoweave.data.ontology.query;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

abstract class ObjectQueryFixtureSupport extends OntologyQueryIntegrationTestSupport {

    protected ObjectFixture createObjectFixture(
            UUID ownerId,
            List<AttributeSpec> attributes
    ) {
        UUID objectTypeId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String mapping = objectMapping(attributes);
        jdbc.update("""
                INSERT INTO data.ontology_object_binding
                    (id, owner_id, object_type_id, status, draft_dataset_kind, draft_dataset_id,
                     draft_mapping, active_dataset_kind, active_dataset_id,
                     active_dataset_version_id, active_mapping_revision)
                VALUES (?, ?, ?, 'ACTIVE', 'PIPELINE', ?, CAST(? AS jsonb), 'PIPELINE', ?, ?, 1)
                """, bindingId, ownerId, objectTypeId, datasetId, mapping, datasetId, UUID.randomUUID());
        insertObjectVersion(versionId, ownerId, bindingId, objectTypeId, datasetId, runId, mapping);
        jdbc.update("""
                UPDATE data.ontology_object_binding
                SET current_index_version_id = ?
                WHERE id = ?
                """, versionId, bindingId);
        return new ObjectFixture(ownerId, objectTypeId, bindingId, versionId, attributes, mapping);
    }

    protected UUID addObjectVersion(
            ObjectFixture fixture,
            List<AttributeSpec> attributes
    ) {
        UUID versionId = UUID.randomUUID();
        String mapping = objectMapping(attributes);
        insertObjectVersion(
                versionId,
                fixture.ownerId(),
                fixture.bindingId(),
                fixture.objectTypeId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                mapping
        );
        jdbc.update("""
                UPDATE data.ontology_object_binding
                SET current_index_version_id = ?
                WHERE id = ?
                """, versionId, fixture.bindingId());
        return versionId;
    }

    protected void insertObject(
            UUID versionId,
            ObjectFixture fixture,
            String primaryKey,
            String title,
            Map<UUID, Object> values
    ) {
        Map<UUID, Object> resolved = new LinkedHashMap<>(values);
        AttributeSpec primary = fixture.attribute(fixture.primaryKeyAttributeId());
        if (!resolved.containsKey(primary.attributeId())) {
            resolved.put(primary.attributeId(), primaryValue(primary.datatype(), primaryKey));
        }
        ObjectNode properties = mapper.createObjectNode();
        resolved.forEach((attributeId, value) -> properties.set(
                attributeId.toString(),
                mapper.valueToTree(value)
        ));
        Object primaryValue = resolved.get(primary.attributeId());
        BigDecimal number = primary.datatype().equals("integer") || primary.datatype().equals("decimal")
                ? new BigDecimal(primaryValue.toString())
                : null;
        LocalDate date = primary.datatype().equals("date")
                ? LocalDate.parse(primaryValue.toString())
                : null;
        Timestamp timestamp = primary.datatype().equals("timestamp")
                || primary.datatype().equals("timestamptz")
                ? Timestamp.from(Instant.parse(primaryValue.toString()))
                : null;
        jdbc.update("""
                INSERT INTO data.ontology_object_instance
                    (version_id, object_type_id, primary_key_value, primary_key_num,
                     primary_key_date, primary_key_ts, title_value, properties)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """,
                versionId,
                fixture.objectTypeId(),
                primaryKey,
                number,
                date,
                timestamp,
                title,
                properties.toString()
        );
        for (Map.Entry<UUID, Object> entry : resolved.entrySet()) {
            insertObjectValue(versionId, primaryKey, fixture.attribute(entry.getKey()), entry.getValue());
        }
    }

    private void insertObjectVersion(
            UUID versionId,
            UUID ownerId,
            UUID bindingId,
            UUID objectTypeId,
            UUID datasetId,
            UUID runId,
            String mapping
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_object_version
                    (id, owner_id, binding_id, object_type_id, source_dataset_kind,
                     source_dataset_id, source_dataset_version_id, source_snapshot_id,
                     mapping_snapshot, mapping_revision, run_id, status, activated_at)
                VALUES (?, ?, ?, ?, 'PIPELINE', ?, ?, 1, CAST(? AS jsonb), 1, ?, 'ACTIVE', now())
                """, versionId, ownerId, bindingId, objectTypeId, datasetId, UUID.randomUUID(),
                mapping, runId);
    }

    private String objectMapping(List<AttributeSpec> attributes) {
        ObjectNode mapping = mapper.createObjectNode();
        mapping.put("primaryKeyAttributeId", attributes.get(0).attributeId().toString());
        mapping.put("titleKeyAttributeId", attributes.get(1).attributeId().toString());
        ArrayNode mapped = mapping.putArray("attributes");
        for (AttributeSpec attribute : attributes) {
            ObjectNode node = mapped.addObject();
            node.put("attributeId", attribute.attributeId().toString());
            node.put("column", attribute.column());
            node.put("datatype", attribute.datatype());
            node.put("required", false);
            node.put("isArray", false);
        }
        mapping.put("revision", 1);
        return mapping.toString();
    }

    private void insertObjectValue(
            UUID versionId,
            String primaryKey,
            AttributeSpec attribute,
            Object value
    ) {
        String text = null;
        BigDecimal number = null;
        LocalDate date = null;
        Timestamp timestamp = null;
        Boolean bool = null;
        switch (attribute.datatype()) {
            case "integer", "decimal" -> number = new BigDecimal(value.toString());
            case "date" -> date = LocalDate.parse(value.toString());
            case "timestamp", "timestamptz" -> timestamp = Timestamp.from(
                    Instant.parse(value.toString())
            );
            case "boolean" -> bool = (Boolean) value;
            default -> text = value.toString();
        }
        jdbc.update("""
                INSERT INTO data.ontology_object_value
                    (version_id, attribute_id, primary_key_value, text_value, num_value,
                     date_value, ts_value, bool_value)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, versionId, attribute.attributeId(), primaryKey, text, number, date,
                timestamp, bool);
    }

    private Object primaryValue(String datatype, String primaryKey) {
        return switch (datatype) {
            case "integer" -> Long.parseLong(primaryKey);
            case "decimal" -> new BigDecimal(primaryKey);
            default -> primaryKey;
        };
    }

    protected record AttributeSpec(UUID attributeId, String column, String datatype) {
    }

    protected record ObjectFixture(
            UUID ownerId,
            UUID objectTypeId,
            UUID bindingId,
            UUID versionId,
            List<AttributeSpec> attributes,
            String mapping
    ) {

        public ObjectFixture {
            attributes = List.copyOf(attributes);
        }

        public UUID primaryKeyAttributeId() {
            return attributes.get(0).attributeId();
        }

        public String primaryKeyDatatype() {
            return attributes.get(0).datatype();
        }

        public AttributeSpec attribute(UUID attributeId) {
            return attributes.stream()
                    .filter(attribute -> attribute.attributeId().equals(attributeId))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
