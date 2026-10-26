package com.ontoweave.data.ontology.binding;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OntologyRunApiTest extends BindingIntegrationTestSupport {

    @Test
    void returnsRunDetailForOwnerWithNullOrEmptyStats() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID runId = insertQueuedRun(ownerId);

        mvc.perform(get("/internal/v1/ontology/runs/{runId}", runId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void hidesRunFromAnotherOwner() throws Exception {
        UUID runId = insertQueuedRun(UUID.randomUUID());

        mvc.perform(get("/internal/v1/ontology/runs/{runId}", runId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_RUN_NOT_FOUND"));
    }

    @Test
    void cancelsQueuedRunAndReturnsUpdatedDetail() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID runId = insertQueuedRun(ownerId);

        mvc.perform(post("/internal/v1/ontology/runs/{runId}/cancel", runId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private UUID insertQueuedRun(UUID ownerId) {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id,
                     target_dataset_kind, target_dataset_version_id, mapping_snapshot,
                     mapping_revision, trigger_type, status, requested_at, attempt, created_at)
                VALUES (?, ?, 'OBJECT_TYPE', ?, NULL, 'SYNC', ?, '{}'::jsonb,
                        1, 'MANUAL', 'QUEUED', now(), 1, now())
                """, runId, ownerId, UUID.randomUUID(), UUID.randomUUID());
        return runId;
    }
}
