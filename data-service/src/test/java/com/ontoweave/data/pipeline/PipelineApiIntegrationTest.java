package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineApiIntegrationTest extends PipelineApiTestSupport {

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
        assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(third.toString());

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
    void usesDefaultPaginationWhenLimitAndOffsetAreOmitted() throws Exception {
        UUID ownerId = UUID.randomUUID();
        create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "first");
        create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "second");

        JsonNode page = json(mvc.perform(get("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(page.path("limit").asInt()).isEqualTo(50);
        assertThat(page.path("offset").asInt()).isZero();
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("items")).hasSize(2);
    }

    @Test
    void rejectsOverlongNamesOnCreateAndRename() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String longName = "x".repeat(201);

        JsonNode invalidCreate = json(mvc.perform(post("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ownerId, UUID.randomUUID(), UUID.randomUUID(), longName)))
                .andExpect(status().isBadRequest())
                .andReturn());
        assertThat(invalidCreate.path("code").asText()).isEqualTo("VALIDATION_ERROR");

        UUID pipelineId = create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "valid");
        mvc.perform(patch("/internal/v1/pipelines/{id}", pipelineId)
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", longName))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countsNameLengthByCodePoint() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String emoji = "\uD83D\uDE00";

        create(ownerId, UUID.randomUUID(), UUID.randomUUID(), emoji.repeat(200));
        JsonNode response = json(mvc.perform(post("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(ownerId, UUID.randomUUID(), UUID.randomUUID(), emoji.repeat(201))))
                .andExpect(status().isBadRequest())
                .andReturn());

        assertThat(response.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void concurrentCreateReturnsOneCreatedAndOneExistingPipeline() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String body = createBody(ownerId, pipelineId, conversationId, "concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(createAfterStart(body, ready, start));
            Future<Integer> second = executor.submit(createAfterStart(body, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 200);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM data.pipeline WHERE id = ?", Integer.class, pipelineId
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM data.pipeline_draft WHERE pipeline_id = ?", Integer.class, pipelineId
        )).isEqualTo(1);
    }

    @Test
    void ordersPipelinesWithTheSameUpdatedAtByIdDescending() throws Exception {
        UUID ownerId = UUID.randomUUID();
        create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "first");
        create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "second");
        create(ownerId, UUID.randomUUID(), UUID.randomUUID(), "third");
        jdbc.update("UPDATE data.pipeline SET updated_at = '2026-01-01T00:00:00Z' WHERE owner_id = ?", ownerId);

        JsonNode page = json(mvc.perform(get("/internal/v1/pipelines")
                        .header("X-Internal-Token", TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andReturn());
        List<String> expected = jdbc.queryForList("""
                SELECT id::text FROM data.pipeline
                WHERE owner_id = ? AND archived_at IS NULL
                ORDER BY updated_at DESC, id DESC
                """, String.class, ownerId);

        List<String> actual = new java.util.ArrayList<>();
        page.path("items").forEach(item -> actual.add(item.path("id").asText()));
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private java.util.concurrent.Callable<Integer> createAfterStart(
            String body,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();
            return mvc.perform(post("/internal/v1/pipelines")
                            .header("X-Internal-Token", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        };
    }
}
