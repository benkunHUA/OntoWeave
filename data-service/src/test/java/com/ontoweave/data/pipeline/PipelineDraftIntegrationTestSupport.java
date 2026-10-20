package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.draft.PipelineDraftCommandService;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PipelineDraftIntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PipelineDraftCommandService service;

    @Autowired
    protected PipelineDraftRepository drafts;

    @Autowired
    protected ObjectMapper mapper;

    protected UUID pipelineId;
    protected UUID ownerId;

    @BeforeEach
    void setUpPipeline() {
        clear();
        pipelineId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline (id, owner_id, conversation_id, name, status)
                VALUES (?, ?, ?, 'integration-pipeline', 'draft')
                """, pipelineId, ownerId, UUID.randomUUID());
    }

    @AfterEach
    void tearDownPipeline() {
        clear();
    }

    protected void insertDraft(JsonNode definition, long revision) {
        jdbc.update("""
                INSERT INTO data.pipeline_draft
                    (pipeline_id, definition, revision, updated_by_type, updated_by_id)
                VALUES (?, CAST(? AS jsonb), ?, 'user', ?)
                """, pipelineId, definition.toString(), revision, ownerId);
    }

    protected long revision() {
        return jdbc.queryForObject(
                "SELECT revision FROM data.pipeline_draft WHERE pipeline_id = ?",
                Long.class,
                pipelineId
        );
    }

    protected int count(String table) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    protected ObjectNode emptyDraft() {
        ObjectNode definition = mapper.createObjectNode();
        definition.put("schemaVersion", 1);
        definition.set("nodes", mapper.createArrayNode());
        definition.set("edges", mapper.createArrayNode());
        return definition;
    }

    protected UUID operationId(long revision) {
        return UUID.nameUUIDFromBytes(("operation-" + revision).getBytes());
    }

    protected UUID turnId(long revision) {
        return UUID.nameUUIDFromBytes(("turn-" + revision).getBytes());
    }

    protected void clear() {
        jdbc.execute("TRUNCATE TABLE data.pipeline CASCADE");
        jdbc.execute("TRUNCATE TABLE data.outbox_event");
    }
}
