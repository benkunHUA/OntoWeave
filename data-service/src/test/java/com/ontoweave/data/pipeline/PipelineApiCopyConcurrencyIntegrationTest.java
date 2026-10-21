package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineApiCopyConcurrencyIntegrationTest extends PipelineApiTestSupport {

    @Test
    void concurrentCopyAndSetOutputLeaveAConsistentTarget() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID sourceId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "source");
        createOutput(ownerId, sourceId);
        UUID secondNodeId = UUID.randomUUID();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/pipelines/{id}/draft/commands", sourceId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addOutputNodeBody(2, secondNodeId)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        UUID targetId = UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        int copyStatus;
        int setOutputStatus;
        try {
            Future<Integer> copy = executor.submit(() -> {
                ready.countDown();
                start.await();
                return copyStatus(sourceId, ownerId, targetId);
            });
            Future<Integer> setOutput = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/pipelines/{id}/draft/commands", sourceId)
                                .header("X-Internal-Token", TOKEN)
                                .param("ownerId", ownerId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(setOutputBody(3, secondNodeId, "orders_second")))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            copyStatus = copy.get(15, TimeUnit.SECONDS);
            setOutputStatus = setOutput.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(setOutputStatus).isEqualTo(200);
        if (copyStatus == 409) {
            assertThat(countWhere("data.pipeline", "id", targetId)).isZero();
            return;
        }
        assertThat(copyStatus).isEqualTo(201);
        List<Map<String, Object>> outputs = jdbc.queryForList("""
                SELECT o.id::text AS output_id, d.id::text AS dataset_id
                FROM data.pipeline_output o
                LEFT JOIN data.pipeline_dataset d ON d.output_id = o.id
                WHERE o.pipeline_id = ?
                """, targetId);
        assertThat(outputs).hasSize(2);
        assertThat(outputs).allSatisfy(output -> assertThat(output.get("dataset_id")).isNotNull());
        Set<String> targetOutputIds = new HashSet<>();
        outputs.forEach(output -> targetOutputIds.add((String) output.get("output_id")));
        JsonNode definition = objectMapper.readTree(jdbc.queryForObject(
                "SELECT definition::text FROM data.pipeline_draft WHERE pipeline_id = ?",
                String.class,
                targetId
        ));
        int outputNodes = 0;
        for (JsonNode node : definition.path("nodes")) {
            if ("output".equals(node.path("type").asText())) {
                outputNodes++;
                assertThat(targetOutputIds).contains(node.path("config").path("outputId").asText());
            }
        }
        assertThat(outputNodes).isEqualTo(2);
    }

    @Test
    void rollsBackTheTargetWhenTheCopiedDraftCannotBeInserted() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID sourceId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "source");
        createOutput(ownerId, sourceId);
        UUID targetId = UUID.randomUUID();
        installFailingDraftTrigger(targetId);
        try {
            copyStatus(sourceId, ownerId, targetId);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_target_draft ON data.pipeline_draft");
            jdbc.execute("DROP FUNCTION IF EXISTS data.fail_target_draft()");
        }

        assertThat(countWhere("data.pipeline", "id", targetId)).isZero();
        assertThat(countWhere("data.pipeline_draft", "pipeline_id", targetId)).isZero();
        assertThat(countWhere("data.pipeline_output", "pipeline_id", targetId)).isZero();
        assertThat(countWhere("data.pipeline_dataset", "pipeline_id", targetId)).isZero();
        assertThat(countWhere("data.pipeline_output", "pipeline_id", sourceId)).isEqualTo(1);
    }

    private int copyStatus(UUID sourceId, UUID ownerId, UUID targetId) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/internal/v1/pipelines/{id}/copy", sourceId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pipelineId", targetId,
                                "conversationId", UUID.randomUUID(),
                                "name", "copy"
                        ))))
                .andReturn().getResponse().getStatus();
    }

    private void installFailingDraftTrigger(UUID targetId) {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_target_draft ON data.pipeline_draft");
        jdbc.execute("DROP FUNCTION IF EXISTS data.fail_target_draft()");
        jdbc.execute("""
                CREATE FUNCTION data.fail_target_draft() RETURNS trigger AS $$
                BEGIN
                  IF NEW.pipeline_id = '%s'::uuid THEN
                    RAISE EXCEPTION 'forced draft failure';
                  END IF;
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(targetId));
        jdbc.execute("""
                CREATE TRIGGER fail_target_draft BEFORE INSERT ON data.pipeline_draft
                FOR EACH ROW EXECUTE FUNCTION data.fail_target_draft()
                """);
    }

    private long countWhere(String table, String column, UUID id) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class,
                id
        );
        return count == null ? 0 : count;
    }
}
