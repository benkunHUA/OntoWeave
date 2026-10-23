package com.ontoweave.data.ontology.query;

import java.util.UUID;

abstract class RelationQueryFixtureSupport extends ObjectQueryFixtureSupport {

    protected UUID createRelationDefinition(
            UUID ownerId,
            ObjectFixture source,
            ObjectFixture target,
            String name,
            String cardinality
    ) {
        UUID relationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_definition
                    (relation_id, owner_id, source_type_id, target_type_id, name, cardinality,
                     source_primary_key_attribute_id, source_primary_key_datatype,
                     target_primary_key_attribute_id, target_primary_key_datatype,
                     definition_revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """,
                relationId,
                ownerId,
                source.objectTypeId(),
                target.objectTypeId(),
                name,
                cardinality,
                source.primaryKeyAttributeId(),
                source.primaryKeyDatatype(),
                target.primaryKeyAttributeId(),
                target.primaryKeyDatatype()
        );
        return relationId;
    }

    protected UUID addFkVersion(
            UUID ownerId,
            UUID relationId,
            ObjectFixture source,
            ObjectFixture target,
            UUID sourceVersionId,
            UUID targetVersionId
    ) {
        return insertRelationVersion(
                ownerId,
                relationId,
                "FK",
                source,
                target,
                sourceVersionId,
                targetVersionId,
                "ACTIVE",
                null
        );
    }

    protected M2mFixture createM2mFixture(
            UUID ownerId,
            UUID relationId,
            ObjectFixture source,
            ObjectFixture target,
            UUID sourceVersionId,
            UUID targetVersionId,
            boolean pointToVersion
    ) {
        UUID bindingId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_binding
                    (id, owner_id, relation_id, status, draft_dataset_kind, draft_dataset_id,
                     draft_mapping, active_dataset_kind, active_dataset_id,
                     active_dataset_version_id, active_mapping_revision)
                VALUES (?, ?, ?, 'ACTIVE', 'PIPELINE', ?, '{}'::jsonb, 'PIPELINE', ?, ?, 1)
                """, bindingId, ownerId, relationId, datasetId, datasetId, UUID.randomUUID());
        UUID versionId = insertRelationVersion(
                ownerId,
                relationId,
                "MANY_TO_MANY",
                source,
                target,
                sourceVersionId,
                targetVersionId,
                "ACTIVE",
                bindingId
        );
        if (pointToVersion) {
            pointM2mTo(bindingId, versionId);
        }
        return new M2mFixture(relationId, bindingId, versionId);
    }

    protected void pointM2mTo(UUID bindingId, UUID versionId) {
        jdbc.update("""
                UPDATE data.ontology_relation_binding
                SET current_index_version_id = ?
                WHERE id = ?
                """, versionId, bindingId);
    }

    protected void insertInstance(
            UUID versionId,
            UUID relationId,
            ObjectFixture source,
            ObjectFixture target,
            String fromPrimaryKey,
            String toPrimaryKey
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_relation_instance
                    (version_id, relation_id, from_object_type_id, from_primary_key,
                     to_object_type_id, to_primary_key)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                versionId,
                relationId,
                source.objectTypeId(),
                fromPrimaryKey,
                target.objectTypeId(),
                toPrimaryKey
        );
    }

    protected UUID insertRelationVersion(
            UUID ownerId,
            UUID relationId,
            String kind,
            ObjectFixture source,
            ObjectFixture target,
            UUID sourceVersionId,
            UUID targetVersionId,
            String status,
            UUID bindingId
    ) {
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_version
                    (id, owner_id, binding_id, relation_id, relation_kind,
                     from_object_type_id, to_object_type_id,
                     from_object_version_id, to_object_version_id,
                     run_id, status, activated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                versionId,
                ownerId,
                bindingId,
                relationId,
                kind,
                source.objectTypeId(),
                target.objectTypeId(),
                sourceVersionId,
                targetVersionId,
                UUID.randomUUID(),
                status
        );
        return versionId;
    }

    protected record M2mFixture(UUID relationId, UUID bindingId, UUID versionId) {
    }
}
