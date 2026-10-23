package com.ontoweave.data.ontology.binding;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelationBindingApiTest extends BindingIntegrationTestSupport {

    @Test
    void manyToManyBindingExposesDraftAndOverview() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID sourceTypeId = UUID.randomUUID();
        UUID targetTypeId = UUID.randomUUID();
        insertDefinition(ownerId, relationId, sourceTypeId, targetTypeId, "many_to_many");
        UUID datasetId = insertRelationDataset(ownerId);

        mvc.perform(put("/internal/v1/ontology/relation-types/{id}/binding", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(ownerId, datasetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.validation.valid").value(true));

        mvc.perform(get("/internal/v1/ontology/relation-types/{id}/binding", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationId").value(relationId.toString()))
                .andExpect(jsonPath("$.draftMapping.fromKeyColumn").value("customer_id"));

        mvc.perform(get("/internal/v1/ontology/relation-types/{id}/overview", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relation.cardinality").value("many_to_many"))
                .andExpect(jsonPath("$.source.objectTypeId").value(sourceTypeId.toString()))
                .andExpect(jsonPath("$.target.objectTypeId").value(targetTypeId.toString()));
    }

    @Test
    void foreignKeyRelationCannotCreateAnIndependentBinding() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        insertDefinition(ownerId, relationId, UUID.randomUUID(), UUID.randomUUID(), "one_to_many");
        UUID datasetId = insertRelationDataset(ownerId);

        mvc.perform(put("/internal/v1/ontology/relation-types/{id}/binding", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(ownerId, datasetId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAPPING_INVALID"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_relation_binding WHERE relation_id = ?",
                Integer.class,
                relationId
        )).isZero();
    }

    @Test
    void unbindRetiresManyToManyVersionsAndClearsActivePointers() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        insertDefinition(ownerId, relationId, UUID.randomUUID(), UUID.randomUUID(), "many_to_many");
        UUID datasetId = insertRelationDataset(ownerId);
        save(ownerId, relationId, datasetId);
        UUID bindingId = jdbc.queryForObject(
                "SELECT id FROM data.ontology_relation_binding WHERE relation_id = ?",
                UUID.class,
                relationId
        );
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                UPDATE data.ontology_relation_binding
                SET active_dataset_kind = 'SYNC', active_dataset_id = ?,
                    active_dataset_version_id = ?, active_mapping_revision = 1,
                    current_index_version_id = ?
                WHERE id = ?
                """, datasetId, UUID.randomUUID(), versionId, bindingId);
        jdbc.update("""
                INSERT INTO data.ontology_relation_version
                    (id, owner_id, binding_id, relation_id, relation_kind,
                     from_object_type_id, to_object_type_id, from_object_version_id,
                     to_object_version_id, run_id, status)
                VALUES (?, ?, ?, ?, 'MANY_TO_MANY', ?, ?, ?, ?, ?, 'ACTIVE')
                """, versionId, ownerId, bindingId, relationId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mvc.perform(delete("/internal/v1/ontology/relation-types/{id}/binding", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("""
                SELECT status FROM data.ontology_relation_binding WHERE id = ?
                """, String.class, bindingId)).isEqualTo("UNBOUND");
        assertThat(jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_relation_binding WHERE id = ?
                """, UUID.class, bindingId)).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM data.ontology_relation_version WHERE id = ?
                """, String.class, versionId)).isEqualTo("RETIRED");
        assertThat(jdbc.queryForObject("""
                SELECT draft_mapping->>'fromKeyColumn' FROM data.ontology_relation_binding WHERE id = ?
                """, String.class, bindingId)).isEqualTo("customer_id");
    }

    @Test
    void overviewAndRunsExposeTheParentObjectRunForForeignKeyRelations() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID sourceTypeId = UUID.randomUUID();
        UUID targetTypeId = UUID.randomUUID();
        UUID sourcePkId = UUID.randomUUID();
        UUID targetPkId = UUID.randomUUID();
        UUID foreignKeyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_definition
                    (relation_id, owner_id, source_type_id, target_type_id, name, cardinality,
                     foreign_key_property_id, foreign_key_datatype,
                     source_primary_key_attribute_id, source_primary_key_datatype,
                     target_primary_key_attribute_id, target_primary_key_datatype,
                     definition_revision)
                VALUES (?, ?, ?, ?, 'customer_orders', 'one_to_many', ?, 'text', ?, 'text', ?, 'text', 3)
                """, relationId, ownerId, sourceTypeId, targetTypeId, foreignKeyId,
                sourcePkId, targetPkId);
        UUID sourceBinding = insertObjectBinding(ownerId, sourceTypeId);
        UUID targetBinding = insertObjectBinding(ownerId, targetTypeId);
        UUID runId = UUID.randomUUID();
        insertRun(ownerId, sourceTypeId, sourceBinding, runId, "SUCCEEDED");
        UUID relationVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_version
                    (id, owner_id, relation_id, relation_kind, from_object_type_id,
                     to_object_type_id, from_object_version_id, to_object_version_id,
                     run_id, status, row_count, skipped_count, activated_at)
                VALUES (?, ?, ?, 'FK', ?, ?, ?, ?, ?, 'ACTIVE', 7, 2, now())
                """, relationVersionId, ownerId, relationId, sourceTypeId, targetTypeId,
                UUID.randomUUID(), UUID.randomUUID(), runId);

        mvc.perform(get("/internal/v1/ontology/relation-types/{id}/overview", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relation.foreignKeyPropertyId")
                        .value(foreignKeyId.toString()))
                .andExpect(jsonPath("$.source.currentIndexVersionId").exists())
                .andExpect(jsonPath("$.target.currentIndexVersionId").exists())
                .andExpect(jsonPath("$.latestRun.id").value(runId.toString()))
                .andExpect(jsonPath("$.latestVersion.rowCount").value(7))
                .andExpect(jsonPath("$.latestVersion.skippedCount").value(2));

        mvc.perform(get("/internal/v1/ontology/relation-types/{id}/binding/runs", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(runId.toString()))
                .andExpect(jsonPath("$[0].subjectId").value(sourceTypeId.toString()));
        assertThat(targetBinding).isNotNull();
    }

    private void save(UUID ownerId, UUID relationId, UUID datasetId) throws Exception {
        mvc.perform(put("/internal/v1/ontology/relation-types/{id}/binding", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(ownerId, datasetId)))
                .andExpect(status().isOk());
    }

    private String saveBody(UUID ownerId, UUID datasetId) {
        return """
                {"ownerId":"%s","baseRevision":0,"datasetKind":"SYNC","datasetId":"%s",
                 "mapping":{"fromKeyColumn":"customer_id","fromKeyDatatype":"text",
                 "toKeyColumn":"order_id","toKeyDatatype":"text"}}
                """.formatted(ownerId, datasetId);
    }

    private UUID insertRelationDataset(UUID ownerId) {
        UUID datasetId = insertSyncDataset(ownerId, "links-" + UUID.randomUUID());
        UUID versionId = jdbc.queryForObject(
                "SELECT current_version_id FROM data.dataset WHERE id = ?",
                UUID.class,
                datasetId
        );
        jdbc.update("""
                UPDATE data.dataset_version
                SET schema_json = '{"fields":[
                    {"name":"customer_id","type":"string","required":true},
                    {"name":"order_id","type":"string","required":true}]}'::jsonb
                WHERE id = ?
                """, versionId);
        return datasetId;
    }

    private void insertDefinition(
            UUID ownerId,
            UUID relationId,
            UUID sourceTypeId,
            UUID targetTypeId,
            String cardinality
    ) {
        UUID foreignKey = "many_to_many".equals(cardinality) ? null : UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_definition
                    (relation_id, owner_id, source_type_id, target_type_id, name, cardinality,
                     foreign_key_property_id, foreign_key_datatype,
                     source_primary_key_attribute_id, source_primary_key_datatype,
                     target_primary_key_attribute_id, target_primary_key_datatype,
                     definition_revision)
                VALUES (?, ?, ?, ?, 'customer_orders', ?, ?, 'text', ?, 'text', ?, 'text', 1)
                """, relationId, ownerId, sourceTypeId, targetTypeId, cardinality,
                foreignKey, UUID.randomUUID(), UUID.randomUUID());
    }

    private UUID insertObjectBinding(UUID ownerId, UUID objectTypeId) {
        UUID bindingId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_object_binding
                    (id, owner_id, object_type_id, draft_dataset_kind, draft_dataset_id,
                     draft_mapping, active_dataset_kind, active_dataset_id,
                     active_dataset_version_id, active_mapping_revision,
                     current_index_version_id)
                VALUES (?, ?, ?, 'SYNC', ?, '{}'::jsonb, 'SYNC', ?, ?, 1, ?)
                """, bindingId, ownerId, objectTypeId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), versionId);
        return bindingId;
    }

    private void insertRun(
            UUID ownerId,
            UUID subjectId,
            UUID bindingId,
            UUID runId,
            String status
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id, target_dataset_kind,
                     target_dataset_version_id, mapping_snapshot, mapping_revision,
                     trigger_type, status, requested_at)
                VALUES (?, ?, 'OBJECT_TYPE', ?, ?, 'SYNC', ?, '{}'::jsonb, 1,
                        'MANUAL', ?, now())
                """, runId, ownerId, subjectId, bindingId, UUID.randomUUID(), status);
    }
}
