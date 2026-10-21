package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineApiArchiveIntegrationTest extends PipelineApiTestSupport {

    @Test
    void archiveRejectsWritesButKeepsReadRoutesAvailable() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "archived");
        UUID nodeId = UUID.randomUUID();
        mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addNodeBody(0, nodeId)))
                .andExpect(status().isOk());

        archive(pipelineId, ownerId).andExpect(status().isNoContent());
        archive(pipelineId, ownerId).andExpect(status().isNoContent());

        JsonNode detail = json(mvc.perform(get("/internal/v1/pipelines/{id}", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detail.path("status").asText()).isEqualTo("archived");
        assertThat(json(mvc.perform(get("/internal/v1/pipelines/{id}/draft", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andReturn()).path("revision").asLong()).isEqualTo(1);
        mvc.perform(get("/internal/v1/pipelines/{id}/check", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("revision", "1"))
                .andExpect(status().isOk());

        JsonNode renamed = json(mvc.perform(patch("/internal/v1/pipelines/{id}", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"renamed\"}"))
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(renamed.path("code").asText()).isEqualTo("PIPELINE_ARCHIVED");

        JsonNode copied = json(mvc.perform(post("/internal/v1/pipelines/{id}/copy", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pipelineId", UUID.randomUUID(),
                                "conversationId", UUID.randomUUID(),
                                "name", "copy"
                        ))))
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(copied.path("code").asText()).isEqualTo("PIPELINE_ARCHIVED");

        assertArchived(json(mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addNodeBody(1, UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andReturn()));
        assertArchived(json(mvc.perform(post("/internal/v1/pipelines/{id}/draft/positions", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"" + nodeId + "\",\"position\":{\"x\":1,\"y\":2}}"))
                .andExpect(status().isConflict())
                .andReturn()));
        assertArchived(json(mvc.perform(post("/internal/v1/pipelines/{id}/turns/{turnId}/undo",
                                pipelineId, UUID.randomUUID())
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentRevision\":1}"))
                .andExpect(status().isConflict())
                .andReturn()));

        assertThat(jdbc.queryForObject(
                "SELECT revision FROM data.pipeline_draft WHERE pipeline_id = ?",
                Long.class,
                pipelineId
        )).isEqualTo(1L);
    }

    private org.springframework.test.web.servlet.ResultActions archive(UUID pipelineId, UUID ownerId)
            throws Exception {
        return mvc.perform(post("/internal/v1/pipelines/{id}/archive", pipelineId)
                .header("X-Internal-Token", TOKEN)
                .param("ownerId", ownerId.toString()));
    }

    private void assertArchived(JsonNode response) {
        assertThat(response.path("code").asText()).isEqualTo("PIPELINE_ARCHIVED");
    }
}
