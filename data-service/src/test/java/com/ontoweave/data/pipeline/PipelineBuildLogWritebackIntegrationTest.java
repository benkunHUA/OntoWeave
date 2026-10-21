package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.execution.build.ExpectationChecker;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildExecutor;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildQueue;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildService;
import com.ontoweave.data.pipeline.execution.build.PipelinePublisher;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import com.ontoweave.data.pipeline.execution.output.WrittenSnapshot;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PlanStep;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineBuildLogWritebackIntegrationTest extends PipelineBuildIntegrationTestSupport {

    private static final String LOG_URI = "http://localhost:8081/#/job/job-log/exceptions";

    @Autowired
    private PipelineBuildExecutor executor;

    @Autowired
    private PipelineBuildService service;

    @Autowired
    private PipelineBuildQueue queue;

    @MockitoBean
    private PipelineDatasetWriter writer;

    @MockitoBean
    private PreviewPlanBuilder plans;

    @MockitoBean
    private ExpectationChecker expectations;

    @MockitoBean
    private PipelinePublisher publisher;

    @Test
    void logWritebackDoesNotOverwriteConcurrentCancellationStageOrHeartbeat() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        OutputFixture output = insertOutput(pipelineId, "orders");
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), frozenOutput(output)
        );
        Instant heartbeat = Instant.now().minusSeconds(60);
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.RUNNING, UUID.randomUUID(),
                heartbeat, heartbeat, null
        );
        insertBuildOutput(
                buildId, output.outputId(), PipelineOutputStatus.RUNNING, mapper.createObjectNode()
        );
        LogicalPlan plan = LogicalPlan.of(
                new PlanStep(output.nodeId().toString(), "dataset",
                        new LogicalPlanNode.Union(), List.of()),
                7L,
                output.nodeId().toString(),
                Map.of()
        );
        when(plans.buildForBuild(any(), anyList(), eq(output.nodeId().toString()))).thenReturn(plan);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(writer.write(any(), any(), any(), any())).thenAnswer(invocation -> {
            writing.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return snapshot();
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> execution = pool.submit(() -> executor.execute(buildId));
            assertThat(writing.await(10, TimeUnit.SECONDS)).isTrue();

            service.requestCancel(pipelineId, buildId, ownerId);
            assertThat(queue.heartbeat(buildId)).isTrue();
            jdbc.update(
                    "UPDATE data.pipeline_build SET stage = 'publishing' WHERE id = ?",
                    buildId
            );
            OffsetDateTime concurrentCancel = jdbc.queryForObject(
                    "SELECT cancel_requested_at FROM data.pipeline_build WHERE id = ?",
                    OffsetDateTime.class,
                    buildId
            );
            Instant concurrentHeartbeat = jdbc.queryForObject(
                    "SELECT heartbeat_at FROM data.pipeline_build WHERE id = ?",
                    Instant.class,
                    buildId
            );

            release.countDown();
            execution.get(30, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject(
                    "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, buildId
            )).isEqualTo("canceled");
            assertThat(jdbc.queryForObject(
                    "SELECT cancel_requested_at FROM data.pipeline_build WHERE id = ?",
                    OffsetDateTime.class,
                    buildId
            )).isEqualTo(concurrentCancel);
            assertThat(jdbc.queryForObject(
                    "SELECT heartbeat_at FROM data.pipeline_build WHERE id = ?",
                    Instant.class,
                    buildId
            )).isAfterOrEqualTo(concurrentHeartbeat);
            assertThat(jdbc.queryForObject(
                    "SELECT stage FROM data.pipeline_build WHERE id = ?", String.class, buildId
            )).isEqualTo("finished");
            assertThat(jdbc.queryForObject(
                    "SELECT log_uri FROM data.pipeline_build WHERE id = ?", String.class, buildId
            )).isEqualTo(LOG_URI);
            assertThat(jdbc.queryForObject(
                    "SELECT log_uri FROM data.pipeline_build_output WHERE build_id = ?",
                    String.class,
                    buildId
            )).isEqualTo(LOG_URI);
            verify(writer).cancel(buildId);
            verify(publisher, never()).publish(any());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private ArrayNode frozenOutput(OutputFixture output) {
        ArrayNode outputs = mapper.createArrayNode();
        ObjectNode frozen = outputs.addObject();
        frozen.put("outputId", output.outputId().toString());
        frozen.put("nodeId", output.nodeId().toString());
        frozen.put("outputType", "dataset");
        frozen.put("name", "orders");
        frozen.put("writeMode", "snapshot");
        frozen.set("primaryKeyColumns", mapper.createArrayNode());
        frozen.set("expectations", mapper.createArrayNode());
        return outputs;
    }

    private WrittenSnapshot snapshot() {
        return new WrittenSnapshot(
                UUID.randomUUID(), "ontoweave", "pipeline_outputs", "orders",
                "build_branch", 101L, mapper.createObjectNode(), 2L, 128L,
                "/tmp/orders", Instant.now(), "job-log", LOG_URI
        );
    }
}
