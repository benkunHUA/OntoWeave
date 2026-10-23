package com.ontoweave.data.ontology.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import com.ontoweave.data.ontology.store.IndexEnqueuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(ObjectBindingApiTest.EnqueuerConfig.class)
class ObjectBindingApiTest extends BindingIntegrationTestSupport {

    @Autowired
    private RecordingEnqueuer enqueuer;

    @BeforeEach
    void resetEnqueuer() {
        enqueuer.requests.clear();
    }

    @Test
    void listsBindableSyncAndPipelineDatasetsForTheOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID syncId = insertSyncDataset(ownerId, "customers", 7);
        UUID pipelineId = insertPipelineDataset(ownerId, "orders", 3);
        insertSyncDataset(UUID.randomUUID(), "someone-else");

        mvc.perform(get("/internal/v1/ontology/bindable-datasets")
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("keyword", "cust"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(syncId.toString()));

        mvc.perform(get("/internal/v1/ontology/bindable-datasets")
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].kind")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("SYNC", "PIPELINE")))
                .andExpect(jsonPath("$.items[*].id").value(
                        org.hamcrest.Matchers.containsInAnyOrder(
                                syncId.toString(), pipelineId.toString()
                        )
                ))
                .andExpect(jsonPath("$.items[?(@.kind == 'SYNC')].versionNumber")
                        .value(org.hamcrest.Matchers.hasItem(7)))
                .andExpect(jsonPath("$.items[?(@.kind == 'PIPELINE')].versionNumber")
                        .value(org.hamcrest.Matchers.hasItem(3)));
    }

    @Test
    void bindableDatasetPageHasCompleteJsonContractWithCurrentVersionNumbers() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID syncId = insertSyncDataset(ownerId, "customers", 7);
        UUID pipelineId = insertPipelineDataset(ownerId, "orders", 3);
        UUID syncVersionId = currentVersion("data.dataset", syncId);
        UUID pipelineVersionId = currentVersion("data.pipeline_dataset", pipelineId);

        mvc.perform(get("/internal/v1/ontology/bindable-datasets")
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "items": [
                            {
                              "kind": "SYNC",
                              "id": "%s",
                              "name": "customers",
                              "versionId": "%s",
                              "versionNumber": 7,
                              "rowCount": 0
                            },
                            {
                              "kind": "PIPELINE",
                              "id": "%s",
                              "name": "orders",
                              "versionId": "%s",
                              "versionNumber": 3,
                              "rowCount": 0
                            }
                          ],
                          "total": 2,
                          "page": 1,
                          "size": 20
                        }
                        """.formatted(
                                syncId, syncVersionId, pipelineId, pipelineVersionId
                        ), true));
    }

    @Test
    void listsDatasetColumnsWithExampleValues() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "columns");

        mvc.perform(get("/internal/v1/ontology/bindable-datasets/{kind}/{datasetId}/columns",
                                DatasetKind.SYNC, datasetId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("id"))
                .andExpect(jsonPath("$[0].type").value("string"))
                .andExpect(jsonPath("$[0].optional").value(false));
    }

    @Test
    void createsBindingWithRevisionOneAndValidatedMapping() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "customers");
        UUID primaryKey = UUID.randomUUID();
        UUID name = UUID.randomUUID();

        MvcResult result = mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding",
                                objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(ownerId, 0, datasetId, primaryKey, name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.validation.valid").value(true))
                .andExpect(jsonPath("$.mapping.revision").value(1))
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("bindingId").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("""
                SELECT revision FROM data.ontology_object_binding
                WHERE object_type_id = ? AND owner_id = ?
                """, Long.class, objectTypeId, ownerId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT validation_state FROM data.ontology_object_binding
                WHERE object_type_id = ?
                """, String.class, objectTypeId)).isEqualTo("OK");
    }

    @Test
    void invalidMappingIsSavedButMarkedInvalid() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "invalid");
        UUID name = UUID.randomUUID();
        String body = """
                {"ownerId":"%s","baseRevision":0,"datasetKind":"SYNC","datasetId":"%s",
                 "mapping":{"primaryKeyAttributeId":"%s","titleKeyAttributeId":null,
                   "attributes":[{"attributeId":"%s","column":"name","datatype":"text",
                     "required":false,"isArray":false}]}}
                """.formatted(ownerId, datasetId, UUID.randomUUID(), name);

        mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.valid").value(false))
                .andExpect(jsonPath("$.validation.blocking[0].code").exists());

        assertThat(jdbc.queryForObject("""
                SELECT validation_state FROM data.ontology_object_binding
                WHERE object_type_id = ?
                """, String.class, objectTypeId)).isEqualTo("NEEDS_REPAIR");
        assertThat(jdbc.queryForObject("""
                SELECT validation_error->>'valid' FROM data.ontology_object_binding
                WHERE object_type_id = ?
                """, String.class, objectTypeId)).isEqualTo("false");
    }

    @Test
    void blocksRequiredAttributeThatIsCompletelyUnmapped() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "missing-required");
        UUID primaryKey = UUID.randomUUID();
        UUID name = UUID.randomUUID();
        UUID missingRequired = UUID.randomUUID();
        String mapping = objectMappingJson(primaryKey, name, List.of(missingRequired));

        mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(ownerId, 0, datasetId, primaryKey, name,
                                List.of(missingRequired))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.valid").value(false))
                .andExpect(jsonPath("$.validation.blocking[*].code")
                        .value(org.hamcrest.Matchers.hasItem("REQUIRED_ATTRIBUTE_UNMAPPED")))
                .andExpect(jsonPath("$.validation.blocking[*].attributeId")
                        .value(org.hamcrest.Matchers.hasItem(missingRequired.toString())));

        assertThat(jdbc.queryForObject("""
                SELECT validation_state FROM data.ontology_object_binding
                WHERE object_type_id = ?
                """, String.class, objectTypeId)).isEqualTo("NEEDS_REPAIR");
        assertThat(jdbc.queryForObject("""
                SELECT validation_error::text FROM data.ontology_object_binding
                WHERE object_type_id = ?
                """, String.class, objectTypeId))
                .contains("REQUIRED_ATTRIBUTE_UNMAPPED", missingRequired.toString());

        mvc.perform(post("/internal/v1/ontology/object-types/{id}/binding/validate", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId + "\",\"mapping\":" + mapping + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.blocking[*].code")
                        .value(org.hamcrest.Matchers.hasItem("REQUIRED_ATTRIBUTE_UNMAPPED")));
    }

    @Test
    void duplicateColumnsAreBlockedByTheOneToOneRule() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "duplicate-columns");
        UUID primaryKey = UUID.randomUUID();
        UUID name = UUID.randomUUID();
        String body = """
                {"ownerId":"%s","baseRevision":0,"datasetKind":"SYNC","datasetId":"%s",
                 "mapping":{"primaryKeyAttributeId":"%s","titleKeyAttributeId":null,
                   "attributes":[
                     {"attributeId":"%s","column":"id","datatype":"text",
                      "required":true,"isArray":false},
                     {"attributeId":"%s","column":"id","datatype":"text",
                      "required":false,"isArray":false}]}}
                """.formatted(ownerId, datasetId, primaryKey, primaryKey, name);

        mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.valid").value(false))
                .andExpect(jsonPath("$.validation.blocking[*].code")
                        .value(org.hamcrest.Matchers.hasItem("COLUMN_ALREADY_MAPPED")));
    }

    @Test
    void titleFallbackIsOnlyAWarning() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "title-warning");
        UUID primaryKey = UUID.randomUUID();
        String body = """
                {"ownerId":"%s","baseRevision":0,"datasetKind":"SYNC","datasetId":"%s",
                 "mapping":{"primaryKeyAttributeId":"%s","titleKeyAttributeId":null,
                   "attributes":[{"attributeId":"%s","column":"id","datatype":"text",
                     "required":true,"isArray":false}]}}
                """.formatted(ownerId, datasetId, primaryKey, primaryKey);

        mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.valid").value(true))
                .andExpect(jsonPath("$.validation.warnings[*].code")
                        .value(org.hamcrest.Matchers.hasItem("TITLE_FALLBACK")));
    }

    @Test
    void rejectsDatasetAlreadyUsedByAnotherBinding() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "shared");
        UUID firstType = UUID.randomUUID();
        UUID secondType = UUID.randomUUID();
        save(firstType, ownerId, 0, datasetId);

        mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding", secondType)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(ownerId, 0, datasetId,
                                UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BINDING_CONFLICT"));
    }

    @Test
    void rejectsStaleRevisionAndReturnsCurrentDraft() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "revision");
        UUID primaryKey = UUID.randomUUID();
        UUID name = UUID.randomUUID();
        save(objectTypeId, ownerId, 0, datasetId);

        mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(ownerId, 9, datasetId, primaryKey, name)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAPPING_REVISION_CONFLICT"))
                .andExpect(jsonPath("$.details.currentRevision").value(1))
                .andExpect(jsonPath("$.details.draft.primaryKeyAttributeId").exists());
    }

    @Test
    void validateRecomputesWithoutChangingRevision() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "validate");
        save(objectTypeId, ownerId, 0, datasetId);

        mvc.perform(post("/internal/v1/ontology/object-types/{id}/binding/validate", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        assertThat(jdbc.queryForObject("""
                SELECT revision FROM data.ontology_object_binding WHERE object_type_id = ?
                """, Long.class, objectTypeId)).isEqualTo(1L);
    }

    @Test
    void generateRejectsRevisionMismatchAndInvalidMapping() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "generate-reject");
        save(objectTypeId, ownerId, 0, datasetId);

        mvc.perform(post("/internal/v1/ontology/object-types/{id}/binding/generate", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId
                                + "\",\"mappingRevision\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAPPING_REVISION_CONFLICT"));

        jdbc.update("""
                UPDATE data.ontology_object_binding
                SET validation_state = 'NEEDS_REPAIR',
                    validation_error = '{"blocking":[{"code":"BAD"}],"warnings":[],"valid":false}'::jsonb
                WHERE object_type_id = ?
                """, objectTypeId);
        jdbc.update("""
                UPDATE data.ontology_object_binding
                SET draft_mapping = '{"primaryKeyAttributeId":null,"attributes":[]}'::jsonb
                WHERE object_type_id = ?
                """, objectTypeId);

        mvc.perform(post("/internal/v1/ontology/object-types/{id}/binding/generate", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId
                                + "\",\"mappingRevision\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAPPING_INVALID"));
        assertThat(enqueuer.requests).isEmpty();
    }

    @Test
    void generateCallsEnqueuerAndWritesOnlyOneQueuedRun() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "generate");
        save(objectTypeId, ownerId, 0, datasetId);

        mvc.perform(post("/internal/v1/ontology/object-types/{id}/binding/generate", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId
                                + "\",\"mappingRevision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").exists());

        assertThat(enqueuer.requests).hasSize(1);
        IndexEnqueuer.ManualIndexRequest request = enqueuer.requests.get(0);
        assertThat(request.subjectKind()).isEqualTo(SubjectKind.OBJECT_TYPE);
        assertThat(request.subjectId()).isEqualTo(objectTypeId);
        assertThat(request.mappingRevision()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_index_run
                WHERE subject_id = ? AND status = 'QUEUED'
                """, Integer.class, objectTypeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_version WHERE object_type_id = ?
                """, Integer.class, objectTypeId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_stage
                """, Integer.class)).isZero();
    }

    @Test
    void runsAreListedNewestFirstAndScopedToOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "runs");
        save(objectTypeId, ownerId, 0, datasetId);
        generate(objectTypeId, ownerId, 1);
        UUID bindingId = jdbc.queryForObject(
                "SELECT id FROM data.ontology_object_binding WHERE object_type_id = ?",
                UUID.class,
                objectTypeId
        );
        jdbc.update("UPDATE data.ontology_index_run SET status = 'RUNNING' WHERE subject_id = ?",
                objectTypeId);
        insertRun(ownerId, objectTypeId, bindingId, datasetId, "SUCCEEDED");
        insertRun(UUID.randomUUID(), objectTypeId, bindingId, datasetId, "SUCCEEDED");

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/binding/runs", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ownerId").value(ownerId.toString()));
    }

    @Test
    void unbindClearsActivePointersRetiresVersionsAndKeepsDraft() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID datasetId = insertSyncDataset(ownerId, "unbind");
        UUID bindingId = save(objectTypeId, ownerId, 0, datasetId);
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_object_version
                    (id, owner_id, binding_id, object_type_id, source_dataset_kind,
                     source_dataset_id, source_dataset_version_id, source_snapshot_id,
                     mapping_snapshot, mapping_revision, run_id, status)
                VALUES (?, ?, ?, ?, 'SYNC', ?, ?, 1, '{}'::jsonb, 1, ?, 'ACTIVE')
                """, versionId, ownerId, bindingId, objectTypeId, datasetId,
                UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                UPDATE data.ontology_object_binding
                SET active_dataset_kind = 'SYNC', active_dataset_id = ?,
                    active_dataset_version_id = ?, active_mapping_revision = 1,
                    current_index_version_id = ?
                WHERE id = ?
                """, datasetId, UUID.randomUUID(), versionId, bindingId);
        UUID relationId = UUID.randomUUID();
        UUID relationBindingId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_binding
                    (id, owner_id, relation_id, draft_dataset_kind, draft_dataset_id,
                     draft_mapping)
                VALUES (?, ?, ?, 'SYNC', ?, '{}'::jsonb)
                """, relationBindingId, ownerId, relationId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.ontology_relation_version
                    (id, owner_id, binding_id, relation_id, relation_kind,
                     from_object_type_id, to_object_type_id, from_object_version_id,
                     to_object_version_id, run_id, status)
                VALUES (?, ?, ?, ?, 'FK', ?, ?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), ownerId, relationBindingId, relationId,
                objectTypeId, UUID.randomUUID(), versionId, UUID.randomUUID(),
                UUID.randomUUID());

        mvc.perform(delete("/internal/v1/ontology/object-types/{id}/binding", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("""
                SELECT status FROM data.ontology_object_binding WHERE id = ?
                """, String.class, bindingId)).isEqualTo("UNBOUND");
        assertThat(jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_object_binding WHERE id = ?
                """, UUID.class, bindingId)).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT draft_mapping->>'primaryKeyAttributeId'
                FROM data.ontology_object_binding WHERE id = ?
                """, String.class, bindingId)).isNotBlank();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM data.ontology_object_version WHERE id = ?
                """, String.class, versionId)).isEqualTo("RETIRED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_relation_version
                WHERE status = 'ACTIVE' AND relation_id = ?
                """, Integer.class, relationId)).isZero();
    }

    private UUID save(
            UUID objectTypeId,
            UUID ownerId,
            long baseRevision,
            UUID datasetId
    ) throws Exception {
        UUID primaryKey = UUID.randomUUID();
        UUID name = UUID.randomUUID();
        MvcResult result = mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding",
                                objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(ownerId, baseRevision, datasetId, primaryKey, name)))
                .andExpect(status().isOk())
                .andReturn();
        String bindingId = mapper.readTree(result.getResponse().getContentAsString())
                .path("bindingId").asText();
        return UUID.fromString(bindingId);
    }

    private void generate(UUID objectTypeId, UUID ownerId, long revision) throws Exception {
        mvc.perform(post("/internal/v1/ontology/object-types/{id}/binding/generate", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId
                                + "\",\"mappingRevision\":" + revision + "}"))
                .andExpect(status().isOk());
    }

    private String saveBody(
            UUID ownerId,
            long baseRevision,
            UUID datasetId,
            UUID primaryKey,
            UUID name
    ) {
        return saveBody(ownerId, baseRevision, datasetId, primaryKey, name, List.of());
    }

    private String saveBody(
            UUID ownerId,
            long baseRevision,
            UUID datasetId,
            UUID primaryKey,
            UUID name,
            List<UUID> extraRequiredAttributeIds
    ) {
        return """
                {"ownerId":"%s","baseRevision":%d,"datasetKind":"SYNC","datasetId":"%s",
                 "mapping":%s}
                """.formatted(
                ownerId,
                baseRevision,
                datasetId,
                objectMappingJson(primaryKey, name, extraRequiredAttributeIds)
        );
    }

    private String objectMappingJson(
            UUID primaryKey,
            UUID name,
            List<UUID> extraRequiredAttributeIds
    ) {
        StringBuilder requiredAttributeIds = new StringBuilder()
                .append('"').append(primaryKey).append('"');
        for (UUID attributeId : extraRequiredAttributeIds) {
            if (attributeId != null) {
                requiredAttributeIds.append(",\"").append(attributeId).append('"');
            }
        }
        return """
                {"primaryKeyAttributeId":"%s","titleKeyAttributeId":"%s",
                 "requiredAttributeIds":[%s],
                 "attributes":[
                   {"attributeId":"%s","column":"id","datatype":"text",
                    "required":true,"isArray":false},
                   {"attributeId":"%s","column":"name","datatype":"text",
                    "required":false,"isArray":false}
                 ]}
                """.formatted(primaryKey, name, requiredAttributeIds, primaryKey, name);
    }

    private void insertRun(
            UUID ownerId,
            UUID subjectId,
            UUID bindingId,
            UUID datasetId,
            String status
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id, target_dataset_kind,
                     target_dataset_version_id, mapping_snapshot, mapping_revision,
                     trigger_type, status, requested_at)
                VALUES (?, ?, 'OBJECT_TYPE', ?, ?, 'SYNC', ?, '{}'::jsonb, 1,
                        'MANUAL', ?, now() + (? * interval '1 second'))
                """, UUID.randomUUID(), ownerId, subjectId, bindingId, UUID.randomUUID(),
                status, status.equals("SUCCEEDED") ? 1 : 0);
    }

    static class RecordingEnqueuer implements IndexEnqueuer {

        private final OntologyIndexRunRepository runs;
        final List<ManualIndexRequest> requests = new java.util.ArrayList<>();

        RecordingEnqueuer(OntologyIndexRunRepository runs) {
            this.runs = runs;
        }

        @Override
        public UUID enqueueManual(ManualIndexRequest request) {
            requests.add(request);
            OntologyIndexRunEntity run = new OntologyIndexRunEntity(
                    UUID.randomUUID(),
                    request.ownerId(),
                    request.subjectKind(),
                    request.subjectId(),
                    request.bindingId(),
                    request.datasetKind(),
                    request.datasetVersionId(),
                    mapper().valueToTree(request.mappingSnapshot()),
                    request.mappingRevision(),
                    TriggerType.MANUAL,
                    null
            );
            return runs.upsertQueued(run).getId();
        }

        @Override
        public UUID enqueueOnPublish(DatasetKind kind, UUID datasetId, UUID versionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID enqueueRetry(UUID runId) {
            throw new UnsupportedOperationException();
        }

        private static com.fasterxml.jackson.databind.ObjectMapper mapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .findAndRegisterModules();
        }
    }

    @TestConfiguration
    static class EnqueuerConfig {

        @Bean
        RecordingEnqueuer recordingEnqueuer(OntologyIndexRunRepository runs) {
            return new RecordingEnqueuer(runs);
        }
    }
}
