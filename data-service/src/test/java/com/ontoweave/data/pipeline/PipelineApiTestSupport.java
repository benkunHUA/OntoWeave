package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PipelineApiTestSupport {

    protected static final String TOKEN = "test-internal-token";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE data.pipeline CASCADE");
        jdbc.execute("TRUNCATE TABLE data.outbox_event");
    }

    protected UUID create(UUID ownerId, UUID pipelineId, UUID conversationId, String name)
            throws Exception {
        JsonNode body = json(mvc.perform(post("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ownerId, pipelineId, conversationId, name)))
                .andExpect(status().isCreated())
                .andReturn());
        return UUID.fromString(body.path("id").asText());
    }

    protected String createBody(UUID ownerId, UUID pipelineId, UUID conversationId, String name)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "ownerId", ownerId,
                "pipelineId", pipelineId,
                "conversationId", conversationId,
                "name", name
        ));
    }

    protected OutputFixture createOutput(UUID ownerId, UUID pipelineId) throws Exception {
        UUID nodeId = UUID.randomUUID();
        mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addOutputNodeBody(nodeId)))
                .andExpect(status().isOk());
        JsonNode withOutput = json(mvc.perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setOutputBody(1, nodeId)))
                .andExpect(status().isOk())
                .andReturn());
        UUID outputId = UUID.fromString(withOutput.path("definition")
                .path("nodes").get(0).path("config").path("outputId").asText());
        return new OutputFixture(nodeId, outputId);
    }

    protected String addNodeBody(long baseRevision, UUID nodeId) throws Exception {
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

    protected String addOutputNodeBody(UUID nodeId) throws Exception {
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

    protected String setOutputBody(long baseRevision, UUID nodeId) throws Exception {
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

    protected ObjectNode command(String type, long baseRevision) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("type", type);
        command.put("baseRevision", baseRevision);
        command.put("operationId", UUID.randomUUID().toString());
        command.put("turnId", UUID.randomUUID().toString());
        return command;
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected int count(String table) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    protected record OutputFixture(UUID nodeId, UUID outputId) {
    }
}
