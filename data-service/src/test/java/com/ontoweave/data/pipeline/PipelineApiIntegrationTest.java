package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PipelineApiIntegrationTest {

    private static final String TOKEN = "test-internal-token";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE data.pipeline CASCADE");
        jdbc.execute("TRUNCATE TABLE data.outbox_event");
    }

    @Test
    void createIsIdempotentAndCreatesOneEmptyDraft() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String body = createBody(ownerId, pipelineId, conversationId, " 订单整理 ");

        JsonNode created = json(mvc.perform(post("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(created.path("id").asText()).isEqualTo(pipelineId.toString());
        assertThat(created.path("status").asText()).isEqualTo("draft");
        assertThat(created.path("name").asText()).isEqualTo("订单整理");
        assertThat(created.path("draftRevision").asLong()).isZero();
        assertThat(created.path("currentVersionId").isNull()).isTrue();

        mvc.perform(post("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(count("data.pipeline")).isEqualTo(1);
        assertThat(count("data.pipeline_draft")).isEqualTo(1);
        JsonNode definition = objectMapper.readTree(jdbc.queryForObject(
                "SELECT definition::text FROM data.pipeline_draft WHERE pipeline_id = ?",
                String.class,
                pipelineId
        ));
        assertThat(definition.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(definition.path("nodes")).isEmpty();
        assertThat(definition.path("edges")).isEmpty();
    }

    @Test
    void rejectsConversationAndPipelineBindingConflicts() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID firstPipelineId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        create(ownerId, firstPipelineId, conversationId, "first");

        JsonNode conversationConflict = json(mvc.perform(post("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ownerId, UUID.randomUUID(), conversationId, "second")))
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(conversationConflict.path("code").asText()).isEqualTo("PIPELINE_BINDING_CONFLICT");

        JsonNode identityConflict = json(mvc.perform(post("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), firstPipelineId, UUID.randomUUID(), "third")))
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(identityConflict.path("code").asText()).isEqualTo("PIPELINE_BINDING_CONFLICT");
        assertThat(count("data.pipeline")).isEqualTo(1);
    }

    @Test
    void listsOnlyUnarchivedPipelinesWithPagingThenRenamesAndArchivesIdempotently() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID first = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "first");
        UUID second = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "second");
        UUID third = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "third");

        mvc.perform(post("/internal/v1/pipelines/{id}/archive", second)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/internal/v1/pipelines/{id}/archive", second)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isNoContent());

        JsonNode page = json(mvc.perform(get("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("limit", "1")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("limit").asInt()).isEqualTo(1);
        assertThat(page.path("items")).hasSize(1);
        assertThat(page.path("items").get(0).path("id").asText())
                .isEqualTo(third.toString());

        JsonNode renamed = json(mvc.perform(patch("/internal/v1/pipelines/{id}", first)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  renamed  \"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(renamed.path("name").asText()).isEqualTo("renamed");

        mvc.perform(patch("/internal/v1/pipelines/{id}", first)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

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

        mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"move_node\",\"baseRevision\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void copiesDraftRemapsOutputsAndDoesNotCopyBuilds() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID sourceId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "source");
        UUID outputNodeId = UUID.randomUUID();

        mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", sourceId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addOutputNodeBody(outputNodeId)))
                .andExpect(status().isOk());

        JsonNode withOutput = json(mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", sourceId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setOutputBody(1, outputNodeId)))
                .andExpect(status().isOk())
                .andReturn());
        UUID sourceOutputId = UUID.fromString(withOutput.path("definition")
                .path("nodes").get(0).path("config").path("outputId").asText());

        UUID copiedId = UUID.randomUUID();
        UUID copiedConversation = UUID.randomUUID();
        JsonNode copied = json(mvc.perform(post("/internal/v1/pipelines/{id}/copy", sourceId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pipelineId", copiedId,
                                "conversationId", copiedConversation,
                                "name", "copied"
                        ))))
                .andExpect(status().isCreated())
                .andReturn());

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
        assertThat(outputs.get(0).get("output_id")).isNotEqualTo(sourceOutputId);
        assertThat(outputs.get(0).get("node_id")).isEqualTo(outputNodeId);
        assertThat(outputs.get(0).get("dataset_id")).isNotNull();

        JsonNode copiedDraft = json(mvc.perform(get("/internal/v1/pipelines/{id}/draft", copiedId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(copiedDraft.path("definition").path("nodes").get(0).path("id").asText())
                .isEqualTo(outputNodeId.toString());
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
    void returnsNotFoundForOwnerMismatchOnEveryPipelineResource() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "private");
        UUID otherOwner = UUID.randomUUID();

        mvc.perform(get("/internal/v1/pipelines/{id}", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", otherOwner.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/v1/pipelines/{id}/draft", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", otherOwner.toString()))
                .andExpect(status().isNotFound());
    }

    private UUID create(UUID ownerId, UUID pipelineId, UUID conversationId, String name) throws Exception {
        JsonNode body = json(mvc.perform(post("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ownerId, pipelineId, conversationId, name)))
                .andExpect(status().isCreated())
                .andReturn());
        return UUID.fromString(body.path("id").asText());
    }

    private String createBody(UUID ownerId, UUID pipelineId, UUID conversationId, String name)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "ownerId", ownerId,
                "pipelineId", pipelineId,
                "conversationId", conversationId,
                "name", name
        ));
    }

    private String addNodeBody(long baseRevision, UUID nodeId) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", nodeId.toString());
        node.put("type", "filter");
        node.put("name", "filter");
        node.putObject("config");
        node.putObject("position").put("x", 10).put("y", 20);
        ObjectNode command = command("add_node", baseRevision);
        command.set("node", node);
        return objectMapper.writeValueAsString(command);
    }

    private String addOutputNodeBody(UUID nodeId) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", nodeId.toString());
        node.put("type", "output");
        node.put("name", "Output");
        node.putObject("config");
        node.putObject("position").put("x", 10).put("y", 20);
        ObjectNode command = command("add_node", 0);
        command.set("node", node);
        return objectMapper.writeValueAsString(command);
    }

    private String setOutputBody(long baseRevision, UUID nodeId) throws Exception {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("nodeId", nodeId.toString());
        output.put("name", "orders_clean");
        output.put("writeMode", "snapshot");
        output.putArray("primaryKeyColumns");
        output.putArray("expectations");
        ObjectNode command = command("set_output", baseRevision);
        command.set("output", output);
        return objectMapper.writeValueAsString(command);
    }

    private ObjectNode command(String type, long baseRevision) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("type", type);
        command.put("baseRevision", baseRevision);
        command.put("operationId", UUID.randomUUID().toString());
        command.put("turnId", UUID.randomUUID().toString());
        return command;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
