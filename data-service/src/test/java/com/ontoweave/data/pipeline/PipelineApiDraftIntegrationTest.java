package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineApiDraftIntegrationTest extends PipelineApiTestSupport {

    @Test
    void acceptsDraftCommandsAndMovesPositionsWithoutIncrementingRevision() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "draft");
        UUID nodeId = UUID.randomUUID();

        JsonNode added = json(mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addNodeBody(0, nodeId)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(added.path("revision").asLong()).isEqualTo(1);
        assertThat(added.path("definition").path("nodes").get(0).path("id").asText())
                .isEqualTo(nodeId.toString());
        assertThat(count("data.pipeline_draft")).isEqualTo(1);

        JsonNode fetched = json(mvc.perform(get("/internal/v1/pipelines/{id}/draft", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(fetched.path("revision").asLong()).isEqualTo(1);
        assertThat(fetched.path("definition").path("nodes")).hasSize(1);

        JsonNode checked = json(mvc.perform(get("/internal/v1/pipelines/{id}/check", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("revision", "1"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(checked.path("pipelineId").asText()).isEqualTo(pipelineId.toString());
        assertThat(checked.path("revision").asLong()).isEqualTo(1);

        JsonNode moved = json(mvc.perform(post("/internal/v1/pipelines/{id}/draft/positions", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"" + nodeId + "\",\"position\":{\"x\":300,\"y\":400}}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(moved.path("revision").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT revision FROM data.pipeline_draft WHERE pipeline_id = ?",
                Long.class,
                pipelineId
        )).isEqualTo(1L);
        assertThat(count("data.pipeline_edit_op")).isEqualTo(1);
    }

    @Test
    void rejectsUnknownDraftCommandType() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "draft");

        JsonNode response = json(mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command("unknown_command", 0))))
                .andExpect(status().isBadRequest())
                .andReturn());

        assertThat(response.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void rejectsMoveNodeThroughDraftCommands() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "draft");
        ObjectNode command = command("move_node", 0);
        command.put("nodeId", UUID.randomUUID().toString());
        command.putObject("position").put("x", 1).put("y", 2);

        mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresRevisionForCheck() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "draft");

        mvc.perform(get("/internal/v1/pipelines/{id}/check", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCommandWithoutBaseRevisionAsConfigInvalid() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "draft");
        ObjectNode command = (ObjectNode) objectMapper.readTree(addNodeBody(0, UUID.randomUUID()));
        command.remove("baseRevision");

        JsonNode response = json(mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andReturn());

        assertThat(response.path("code").asText()).isEqualTo("CONFIG_INVALID");
    }

    @Test
    void undoRequiresCurrentRevisionAndRestoresTheTurn() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "draft");
        UUID nodeId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        ObjectNode add = (ObjectNode) objectMapper.readTree(addNodeBody(0, nodeId));
        add.put("turnId", turnId.toString());
        mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(add)))
                .andExpect(status().isOk());

        JsonNode missing = json(mvc.perform(post("/internal/v1/pipelines/{id}/turns/{turnId}/undo",
                                pipelineId, turnId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn());
        assertThat(missing.path("code").asText()).isEqualTo("VALIDATION_ERROR");

        JsonNode undone = json(mvc.perform(post("/internal/v1/pipelines/{id}/turns/{turnId}/undo",
                                pipelineId, turnId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentRevision\":1}"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(undone.path("revision").asLong()).isEqualTo(2);
        assertThat(undone.path("definition").path("nodes")).isEmpty();
    }
}
