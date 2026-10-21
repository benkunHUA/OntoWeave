package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PipelineApiArchiveRaceIntegrationTest extends PipelineApiTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private DataSource dataSource;

    @Test
    void archiveAndRenameSerializeWithoutResurrection() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID archiveFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "archive-first");
        RaceResult archiveFirst = race(archiveFirstId, ownerId, true, () -> rename(archiveFirstId, ownerId, "late"));
        assertConflict(archiveFirst.second());
        assertThat(status(archiveFirstId)).isEqualTo("archived");
        assertThat(name(archiveFirstId)).isEqualTo("archive-first");

        UUID writerFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "writer-first");
        RaceResult writerFirst = race(writerFirstId, ownerId, false, () -> rename(writerFirstId, ownerId, "winner"));
        assertThat(writerFirst.first().status()).isEqualTo(200);
        assertThat(writerFirst.second().status()).isEqualTo(204);
        assertThat(name(writerFirstId)).isEqualTo("winner");
        assertThat(status(writerFirstId)).isEqualTo("archived");
    }

    @Test
    void archiveAndCopySerializeWithoutLosingACompletedCopy() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID archiveFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "copy-archive-first");
        createOutput(ownerId, archiveFirstId);
        UUID rejectedTarget = UUID.randomUUID();
        RaceResult archiveFirst = race(archiveFirstId, ownerId, true,
                () -> copy(archiveFirstId, ownerId, rejectedTarget));
        assertConflict(archiveFirst.second());
        assertThat(pipelineCount(rejectedTarget)).isZero();
        assertThat(status(archiveFirstId)).isEqualTo("archived");

        UUID writerFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "copy-writer-first");
        createOutput(ownerId, writerFirstId);
        UUID copiedTarget = UUID.randomUUID();
        RaceResult writerFirst = race(writerFirstId, ownerId, false,
                () -> copy(writerFirstId, ownerId, copiedTarget));
        assertThat(writerFirst.first().status()).isEqualTo(201);
        assertThat(writerFirst.second().status()).isEqualTo(204);
        assertThat(pipelineCount(copiedTarget)).isEqualTo(1);
        assertThat(status(writerFirstId)).isEqualTo("archived");
    }

    @Test
    void archiveAndDraftCommandSerializeWithoutPostArchiveWrites() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID archiveFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "command-archive-first");
        RaceResult archiveFirst = race(archiveFirstId, ownerId, true,
                () -> addNode(archiveFirstId, ownerId, UUID.randomUUID(), 0, UUID.randomUUID()));
        assertConflict(archiveFirst.second());
        assertThat(revision(archiveFirstId)).isZero();
        assertThat(status(archiveFirstId)).isEqualTo("archived");

        UUID writerFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "command-writer-first");
        UUID nodeId = UUID.randomUUID();
        RaceResult writerFirst = race(writerFirstId, ownerId, false,
                () -> addNode(writerFirstId, ownerId, nodeId, 0, UUID.randomUUID()));
        assertThat(writerFirst.first().status()).isEqualTo(200);
        assertThat(writerFirst.second().status()).isEqualTo(204);
        assertThat(revision(writerFirstId)).isEqualTo(1);
        assertThat(nodeExists(writerFirstId, nodeId)).isTrue();
        assertThat(status(writerFirstId)).isEqualTo("archived");
    }

    @Test
    void archiveAndPositionUpdateSerializeWithoutPostArchiveWrites() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID archiveFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "move-archive-first");
        UUID archiveNode = UUID.randomUUID();
        addNode(archiveFirstId, ownerId, archiveNode, 0, UUID.randomUUID());
        RaceResult archiveFirst = race(archiveFirstId, ownerId, true,
                () -> move(archiveFirstId, ownerId, archiveNode, 300));
        assertConflict(archiveFirst.second());
        assertThat(positionX(archiveFirstId, archiveNode)).isEqualTo(10);
        assertThat(status(archiveFirstId)).isEqualTo("archived");

        UUID writerFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "move-writer-first");
        UUID writerNode = UUID.randomUUID();
        addNode(writerFirstId, ownerId, writerNode, 0, UUID.randomUUID());
        RaceResult writerFirst = race(writerFirstId, ownerId, false,
                () -> move(writerFirstId, ownerId, writerNode, 300));
        assertThat(writerFirst.first().status()).isEqualTo(200);
        assertThat(writerFirst.second().status()).isEqualTo(204);
        assertThat(positionX(writerFirstId, writerNode)).isEqualTo(300);
        assertThat(status(writerFirstId)).isEqualTo("archived");
    }

    @Test
    void archiveAndUndoSerializeWithoutPostArchiveWrites() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID archiveFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "undo-archive-first");
        UUID archiveTurn = UUID.randomUUID();
        addNode(archiveFirstId, ownerId, UUID.randomUUID(), 0, archiveTurn);
        RaceResult archiveFirst = race(archiveFirstId, ownerId, true,
                () -> undo(archiveFirstId, ownerId, archiveTurn, 1));
        assertConflict(archiveFirst.second());
        assertThat(revision(archiveFirstId)).isEqualTo(1);
        assertThat(status(archiveFirstId)).isEqualTo("archived");

        UUID writerFirstId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "undo-writer-first");
        UUID writerTurn = UUID.randomUUID();
        addNode(writerFirstId, ownerId, UUID.randomUUID(), 0, writerTurn);
        RaceResult writerFirst = race(writerFirstId, ownerId, false,
                () -> undo(writerFirstId, ownerId, writerTurn, 1));
        assertThat(writerFirst.first().status()).isEqualTo(200);
        assertThat(writerFirst.second().status()).isEqualTo(204);
        assertThat(revision(writerFirstId)).isEqualTo(2);
        assertThat(status(writerFirstId)).isEqualTo("archived");
    }

    private RaceResult race(UUID pipelineId, UUID ownerId, boolean archiveFirst, Request writer)
            throws Exception {
        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (PreparedStatement statement = lock.prepareStatement(
                    "SELECT id FROM data.pipeline WHERE id = ? FOR UPDATE"
            )) {
                statement.setObject(1, pipelineId);
                assertThat(statement.executeQuery().next()).isTrue();
            }
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Request archiveRequest = () -> archive(pipelineId, ownerId);
                Request firstRequest = archiveFirst ? archiveRequest : writer;
                Request secondRequest = archiveFirst ? writer : archiveRequest;
                Future<ApiResponse> first = executor.submit(firstRequest::run);
                awaitLockWaiters(1);
                Future<ApiResponse> second = executor.submit(secondRequest::run);
                awaitLockWaiters(2);
                lock.rollback();
                return new RaceResult(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private void awaitLockWaiters(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock'
                    """, Integer.class);
            if (waiting != null && waiting >= expected) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("expected " + expected + " lock waiter(s)");
    }

    private ApiResponse archive(UUID pipelineId, UUID ownerId) throws Exception {
        return perform(post("/internal/v1/pipelines/{id}/archive", pipelineId)
                .header("X-Internal-Token", TOKEN)
                .param("ownerId", ownerId.toString()));
    }

    private ApiResponse rename(UUID pipelineId, UUID ownerId, String name) throws Exception {
        return perform(patch("/internal/v1/pipelines/{id}", pipelineId)
                .header("X-Internal-Token", TOKEN)
                .param("ownerId", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))));
    }

    private ApiResponse copy(UUID sourceId, UUID ownerId, UUID targetId) throws Exception {
        return perform(post("/internal/v1/pipelines/{id}/copy", sourceId)
                .header("X-Internal-Token", TOKEN)
                .param("ownerId", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "pipelineId", targetId,
                        "conversationId", UUID.randomUUID(),
                        "name", "copy"
                ))));
    }

    private ApiResponse addNode(
            UUID pipelineId,
            UUID ownerId,
            UUID nodeId,
            long baseRevision,
            UUID turnId
    ) throws Exception {
        ObjectNode command = (ObjectNode) objectMapper.readTree(addNodeBody(baseRevision, nodeId));
        command.put("turnId", turnId.toString());
        return perform(post("/internal/v1/pipelines/{id}/draft/commands", pipelineId)
                .header("X-Internal-Token", TOKEN)
                .param("ownerId", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)));
    }

    private ApiResponse move(UUID pipelineId, UUID ownerId, UUID nodeId, int x) throws Exception {
        return perform(post("/internal/v1/pipelines/{id}/draft/positions", pipelineId)
                .header("X-Internal-Token", TOKEN)
                .param("ownerId", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodeId\":\"" + nodeId + "\",\"position\":{\"x\":" + x + ",\"y\":20}}"));
    }

    private ApiResponse undo(UUID pipelineId, UUID ownerId, UUID turnId, long revision) throws Exception {
        return perform(post("/internal/v1/pipelines/{id}/turns/{turnId}/undo", pipelineId, turnId)
                .header("X-Internal-Token", TOKEN)
                .param("ownerId", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentRevision\":" + revision + "}"));
    }

    private ApiResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        var result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString();
        JsonNode body = content.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(content);
        return new ApiResponse(result.getResponse().getStatus(), body);
    }

    private void assertConflict(ApiResponse response) {
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().path("code").asText()).isEqualTo("PIPELINE_ARCHIVED");
    }

    private String status(UUID pipelineId) {
        return jdbc.queryForObject("SELECT status FROM data.pipeline WHERE id = ?", String.class, pipelineId);
    }

    private String name(UUID pipelineId) {
        return jdbc.queryForObject("SELECT name FROM data.pipeline WHERE id = ?", String.class, pipelineId);
    }

    private long revision(UUID pipelineId) {
        Long value = jdbc.queryForObject(
                "SELECT revision FROM data.pipeline_draft WHERE pipeline_id = ?", Long.class, pipelineId
        );
        return value == null ? 0 : value;
    }

    private int pipelineCount(UUID pipelineId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM data.pipeline WHERE id = ?", Integer.class, pipelineId
        );
        return count == null ? 0 : count;
    }

    private boolean nodeExists(UUID pipelineId, UUID nodeId) {
        JsonNode definition = definition(pipelineId);
        for (JsonNode node : definition.path("nodes")) {
            if (nodeId.toString().equals(node.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private int positionX(UUID pipelineId, UUID nodeId) {
        for (JsonNode node : definition(pipelineId).path("nodes")) {
            if (nodeId.toString().equals(node.path("id").asText())) {
                return node.path("position").path("x").asInt();
            }
        }
        throw new AssertionError("missing node " + nodeId);
    }

    private JsonNode definition(UUID pipelineId) {
        try {
            return objectMapper.readTree(jdbc.queryForObject(
                    "SELECT definition::text FROM data.pipeline_draft WHERE pipeline_id = ?",
                    String.class,
                    pipelineId
            ));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface Request {
        ApiResponse run() throws Exception;
    }

    private record ApiResponse(int status, JsonNode body) {
    }

    private record RaceResult(ApiResponse first, ApiResponse second) {
    }
}
