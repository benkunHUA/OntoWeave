package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineApiCopyIntegrationTest extends PipelineApiTestSupport {

    @Test
    void copiesDraftRemapsOutputsAndDoesNotCopyBuilds() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID sourceId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "source");
        OutputFixture sourceOutput = createOutput(ownerId, sourceId);

        UUID copiedId = UUID.randomUUID();
        JsonNode copied = copy(sourceId, ownerId, copiedId, UUID.randomUUID(), "copied", 201);

        assertThat(copied.path("id").asText()).isEqualTo(copiedId.toString());
        assertThat(copied.path("status").asText()).isEqualTo("draft");
        assertThat(copied.path("currentVersionId").isNull()).isTrue();
        assertThat(copied.path("draftRevision").asLong()).isZero();

        List<Map<String, Object>> outputs = jdbc.queryForList("""
                SELECT o.id AS output_id, o.node_id, d.id AS dataset_id
                FROM data.pipeline_output o
                JOIN data.pipeline_dataset d ON d.output_id = o.id
                WHERE o.pipeline_id = ?
                """, copiedId);
        assertThat(outputs).hasSize(1);
        assertThat(outputs.get(0).get("output_id")).isNotEqualTo(sourceOutput.outputId());
        assertThat(outputs.get(0).get("node_id")).isEqualTo(sourceOutput.nodeId());
        assertThat(outputs.get(0).get("dataset_id")).isNotNull();

        JsonNode copiedDraft = json(mvc.perform(get("/internal/v1/pipelines/{id}/draft", copiedId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(copiedDraft.path("definition").path("nodes").get(0).path("id").asText())
                .isEqualTo(sourceOutput.nodeId().toString());
        assertThat(copiedDraft.path("definition").path("nodes").get(0).path("config").path("outputId").asText())
                .isEqualTo(outputs.get(0).get("output_id").toString());
        assertThat(count("data.pipeline_build")).isZero();
        assertThat(count("data.pipeline_version")).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM data.pipeline_edit_op WHERE pipeline_id = ?",
                Integer.class,
                copiedId
        )).isZero();
    }

    @Test
    void rejectsCopyWhenOutputDatasetIsMissingOrArchived() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID missingSource = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "missing");
        createOutput(ownerId, missingSource);
        jdbc.update("DELETE FROM data.pipeline_dataset WHERE pipeline_id = ?", missingSource);

        JsonNode missing = copy(
                missingSource,
                ownerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "copy-missing",
                409
        );
        assertThat(missing.path("code").asText()).isEqualTo("OUTPUT_CONFIGURATION_INCOMPLETE");

        UUID archivedSource = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "archived");
        createOutput(ownerId, archivedSource);
        jdbc.update("UPDATE data.pipeline_dataset SET archived_at = now() WHERE pipeline_id = ?", archivedSource);

        JsonNode archived = copy(
                archivedSource,
                ownerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "copy-archived",
                409
        );
        assertThat(archived.path("code").asText()).isEqualTo("OUTPUT_CONFIGURATION_INCOMPLETE");
    }

    @Test
    void rejectsCopyWhenTargetPipelineOrConversationIsAlreadyBound() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID sourceId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "source");
        UUID existingId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "existing");
        UUID existingConversationId = UUID.fromString(jdbc.queryForObject(
                "SELECT conversation_id::text FROM data.pipeline WHERE id = ?",
                String.class,
                existingId
        ));

        assertThat(copy(sourceId, ownerId, existingId, UUID.randomUUID(), "duplicate-id", 409)
                .path("code").asText()).isEqualTo("PIPELINE_BINDING_CONFLICT");
        assertThat(copy(sourceId, ownerId, UUID.randomUUID(), existingConversationId, "duplicate-conv", 409)
                .path("code").asText()).isEqualTo("PIPELINE_BINDING_CONFLICT");
    }

    @Test
    void rejectsOverlongCopyName() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID sourceId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "source");

        JsonNode response = copy(
                sourceId,
                ownerId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "x".repeat(201),
                400
        );

        assertThat(response.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    private JsonNode copy(
            UUID sourceId,
            UUID ownerId,
            UUID pipelineId,
            UUID conversationId,
            String name,
            int expectedStatus
    ) throws Exception {
        return json(mvc.perform(post("/internal/v1/pipelines/{id}/copy", sourceId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pipelineId", pipelineId,
                                "conversationId", conversationId,
                                "name", name
                        ))))
                .andExpect(status().is(expectedStatus))
                .andReturn());
    }
}
