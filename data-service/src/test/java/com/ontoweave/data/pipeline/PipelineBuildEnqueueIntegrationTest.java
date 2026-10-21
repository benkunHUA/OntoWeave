package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.check.PipelineCheckResult;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.check.PipelineStage;
import com.ontoweave.data.pipeline.execution.build.BuildResponse;
import com.ontoweave.data.pipeline.execution.build.CreateBuildRequest;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PipelineBuildEnqueueIntegrationTest extends PipelineBuildIntegrationTestSupport {

    private static final long REVISION = 7L;

    @Autowired
    private PipelineBuildService service;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PipelineCheckService checks;

    private UUID ownerId;
    private UUID pipelineId;

    @BeforeEach
    void createPipeline() {
        ownerId = UUID.randomUUID();
        pipelineId = insertPipeline(ownerId);
        insertDraft(pipelineId, REVISION);
        insertOutput(pipelineId, "orders");
        when(checks.check(pipelineId, ownerId, REVISION)).thenReturn(new PipelineCheckResult(
                pipelineId, REVISION, PipelineStage.READY, List.of(), List.of(), List.of()
        ));
    }

    @Test
    void postEnqueuesOneQueuedManualBuildWithTheirOutputs() throws Exception {
        UUID operationId = UUID.randomUUID();

        mvc.perform(post("/internal/v1/pipelines/{pipelineId}/builds", pipelineId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draftRevision\":7,\"operationId\":\"" + operationId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.trigger").value("manual"))
                .andExpect(jsonPath("$.outputs.length()").value(1));

        assertThat(count("data.pipeline_build")).isEqualTo(1);
        assertThat(count("data.pipeline_build_output")).isEqualTo(1);
    }

    @Test
    void archivedPipelineCannotEnqueueAnotherBuild() {
        jdbc.update(
                "UPDATE data.pipeline SET status = 'archived', archived_at = now() WHERE id = ?",
                pipelineId
        );

        assertThatThrownBy(() -> service.enqueue(
                pipelineId, ownerId, new CreateBuildRequest(REVISION, UUID.randomUUID())
        )).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.status().value()).isEqualTo(409);
            assertThat(error.code()).isEqualTo("PIPELINE_ARCHIVED");
        });
        assertThat(count("data.pipeline_build")).isZero();
    }

    @Test
    void concurrentSameOperationReturnsTheSameBuildWithoutUniqueConflict() throws Exception {
        UUID operationId = UUID.randomUUID();

        List<Object> outcomes = runConcurrently(
                () -> service.enqueue(pipelineId, ownerId, new CreateBuildRequest(REVISION, operationId)),
                () -> service.enqueue(pipelineId, ownerId, new CreateBuildRequest(REVISION, operationId))
        );

        assertThat(outcomes).allMatch(BuildResponse.class::isInstance);
        BuildResponse first = (BuildResponse) outcomes.get(0);
        BuildResponse second = (BuildResponse) outcomes.get(1);
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(count("data.pipeline_build")).isEqualTo(1);
        assertThat(count("data.pipeline_build_output")).isEqualTo(1);
    }

    @Test
    void concurrentDifferentOperationsCreatesOneBuildAndReturnsConflictForTheOther() throws Exception {
        List<Object> outcomes = runConcurrently(
                () -> service.enqueue(
                        pipelineId, ownerId, new CreateBuildRequest(REVISION, UUID.randomUUID())
                ),
                () -> service.enqueue(
                        pipelineId, ownerId, new CreateBuildRequest(REVISION, UUID.randomUUID())
                )
        );

        assertThat(outcomes).filteredOn(BuildResponse.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ApiException.class::isInstance).singleElement()
                .satisfies(error -> {
                    ApiException conflict = (ApiException) error;
                    assertThat(conflict.status().value()).isEqualTo(409);
                    assertThat(conflict.code()).isEqualTo("BUILD_ACTIVE");
                });
        assertThat(count("data.pipeline_build")).isEqualTo(1);
        assertThat(count("data.pipeline_build_output")).isEqualTo(1);
    }

    private List<Object> runConcurrently(ThrowingSupplier first, ThrowingSupplier second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> left = pool.submit(() -> runAfterBarrier(first, ready, start));
            Future<Object> right = pool.submit(() -> runAfterBarrier(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(left.get(30, TimeUnit.SECONDS), right.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private Object runAfterBarrier(
            ThrowingSupplier supplier,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent test barrier timed out");
        }
        try {
            return supplier.get();
        } catch (Throwable error) {
            return error;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }
}
