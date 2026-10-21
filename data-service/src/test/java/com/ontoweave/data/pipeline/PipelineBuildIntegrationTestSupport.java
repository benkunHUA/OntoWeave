package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PipelineBuildIntegrationTestSupport {

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
    protected ObjectMapper mapper;

    @BeforeEach
    void cleanBuildDatabase() {
        jdbc.execute("TRUNCATE TABLE data.pipeline CASCADE");
        jdbc.execute("TRUNCATE TABLE data.outbox_event");
    }

    protected UUID insertPipeline(UUID ownerId) {
        UUID pipelineId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline (id, owner_id, conversation_id, name, status)
                VALUES (?, ?, ?, ?, 'draft')
                """, pipelineId, ownerId, UUID.randomUUID(), "build-" + pipelineId);
        return pipelineId;
    }

    protected void insertDraft(UUID pipelineId, long revision) {
        ObjectNode definition = mapper.createObjectNode();
        definition.put("schemaVersion", 1);
        definition.putArray("nodes");
        definition.putArray("edges");
        jdbc.update("""
                INSERT INTO data.pipeline_draft
                    (pipeline_id, definition, revision, updated_by_type, updated_by_id)
                VALUES (?, CAST(? AS jsonb), ?, 'user', ?)
                """, pipelineId, definition.toString(), revision, UUID.randomUUID());
    }

    protected OutputFixture insertOutput(UUID pipelineId, String name) {
        UUID outputId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_output
                    (id, pipeline_id, node_id, output_type, name, status)
                VALUES (?, ?, ?, 'dataset', ?, 'active')
                """, outputId, pipelineId, nodeId, name);
        jdbc.update("""
                INSERT INTO data.pipeline_dataset
                    (id, output_id, pipeline_id, name, write_mode, primary_key_columns, expectations)
                VALUES (?, ?, ?, ?, 'snapshot', CAST('[]' AS jsonb), CAST('[]' AS jsonb))
                """, datasetId, outputId, pipelineId, name);
        return new OutputFixture(outputId, nodeId, datasetId);
    }

    protected UUID insertVersion(UUID pipelineId, int versionNumber, JsonNode definition, JsonNode outputs) {
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_version
                    (id, pipeline_id, version_number, definition, outputs, definition_hash,
                     input_refs, source_draft_revision, created_by)
                VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, CAST('[]' AS jsonb), 7, ?)
                """, versionId, pipelineId, versionNumber, definition.toString(), outputs.toString(),
                "hash-" + UUID.randomUUID(), UUID.randomUUID());
        return versionId;
    }

    protected UUID insertBuild(
            UUID pipelineId,
            UUID versionId,
            PipelineBuildStatus status,
            UUID operationId
    ) {
        return insertBuild(pipelineId, versionId, status, operationId, null, null, null);
    }

    protected UUID insertBuild(
            UUID pipelineId,
            UUID versionId,
            PipelineBuildStatus status,
            UUID operationId,
            Instant startedAt,
            Instant heartbeatAt,
            Instant cancelRequestedAt
    ) {
        UUID buildId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_build
                    (id, pipeline_id, pipeline_version_id, status, trigger_type, operation_id,
                     requested_by, resolved_inputs, stats, started_at, heartbeat_at, cancel_requested_at)
                VALUES (?, ?, ?, ?, 'manual', ?, ?, CAST('[]' AS jsonb), CAST('{}' AS jsonb), ?, ?, ?)
                """, buildId, pipelineId, versionId, status.name().toLowerCase(), operationId,
                UUID.randomUUID(), timestamp(startedAt), timestamp(heartbeatAt),
                timestamp(cancelRequestedAt));
        return buildId;
    }

    protected UUID insertBuildOutput(
            UUID buildId,
            UUID outputId,
            PipelineOutputStatus status,
            JsonNode result
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_build_output
                    (id, build_id, output_id, output_type, status, result, stats)
                VALUES (?, ?, ?, 'dataset', ?, CAST(? AS jsonb), CAST('{}' AS jsonb))
                """, id, buildId, outputId, status.name().toLowerCase(), result.toString());
        return id;
    }

    protected ObjectNode buildResult(String table, long snapshotId) {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode snapshot = result.putObject("snapshot");
        snapshot.put("icebergCatalog", "ontoweave");
        snapshot.put("icebergNamespace", "pipeline_outputs");
        snapshot.put("icebergTable", table);
        snapshot.put("icebergSnapshotId", snapshotId);
        snapshot.put("branch", "build_branch");
        snapshot.set("schema", mapper.createObjectNode());
        snapshot.put("rowCount", 2);
        snapshot.put("byteSize", 128);
        snapshot.put("storagePath", "/tmp/" + table);
        snapshot.put("committedAt", Instant.now().toString());
        return result;
    }

    protected int count(String table) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    protected record OutputFixture(UUID outputId, UUID nodeId, UUID datasetId) {
    }
}
