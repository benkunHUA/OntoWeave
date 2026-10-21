package com.ontoweave.data.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineApiOwnerIntegrationTest extends PipelineApiTestSupport {

    @Test
    void returnsNotFoundForOwnerMismatchOnEveryOwnerScopedRoute() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "private");
        UUID otherOwner = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        mvc.perform(get("/internal/v1/pipelines/{id}/draft", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", otherOwner.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/v1/pipelines/{id}/draft/positions", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", otherOwner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"" + nodeId + "\",\"position\":{\"x\":1,\"y\":2}}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/v1/pipelines/{id}/check", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", otherOwner.toString())
                        .param("revision", "0"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/v1/pipelines/{id}/turns/{turnId}/undo", pipelineId, UUID.randomUUID())
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", otherOwner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentRevision\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/v1/pipelines/{id}/datasets/{datasetId}/sources", pipelineId, UUID.randomUUID())
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", otherOwner.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/v1/pipelines/{id}/copy", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", otherOwner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pipelineId", UUID.randomUUID(),
                                "conversationId", UUID.randomUUID(),
                                "name", "copy"
                        ))))
                .andExpect(status().isNotFound());
    }
}
