package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineBuildTrigger;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import com.ontoweave.data.pipeline.execution.output.WrittenSnapshot;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PlanStep;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.pipeline.execution.preview.PipelinePreviewService;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PipelineBuildExecutorTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID BUILD_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID OUTPUT_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();
    private static final UUID DATASET_ID = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineBuildRepository builds = mock(PipelineBuildRepository.class);
    private final PipelineBuildOutputRepository buildOutputs = mock(PipelineBuildOutputRepository.class);
    private final PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
    private final PipelineOutputRepository outputs = mock(PipelineOutputRepository.class);
    private final PreviewPlanBuilder plans = mock(PreviewPlanBuilder.class);
    private final PipelineDatasetWriter writer = mock(PipelineDatasetWriter.class);
    private final ExpectationChecker expectations = mock(ExpectationChecker.class);
    private final PipelinePublisher publisher = mock(PipelinePublisher.class);
    private final PipelinePreviewService previews = mock(PipelinePreviewService.class);
    private final PipelineBuildBranchCleaner branchCleaner = mock(PipelineBuildBranchCleaner.class);
    private final PipelineBuildExecutor executor = new PipelineBuildExecutor(
            builds, buildOutputs, versions, outputs, plans, writer,
            expectations, publisher, previews, branchCleaner, mapper
    );

    @Test
    void releasesPreviewBeforeBuildingWithBuildModePlan() {
        PipelineBuildEntity build = build();
        PipelineVersionEntity version = version();
        PipelineBuildOutputEntity row = buildOutput();
        PipelineOutputEntity output = new PipelineOutputEntity(
                OUTPUT_ID, PIPELINE_ID, NODE_ID, PipelineOutputType.DATASET, "orders_out"
        );
        LogicalPlan plan = LogicalPlan.of(
                new PlanStep(NODE_ID.toString(), "dataset", new LogicalPlanNode.Union(), List.of()),
                version.getSourceDraftRevision(), NODE_ID.toString(), Map.of()
        );
        whenBuildReady(build, version, row, output);
        when(plans.buildForBuild(eq(version), anyList(), eq(NODE_ID.toString()))).thenReturn(plan);
        when(writer.write(eq(BUILD_ID), eq(OUTPUT_ID), eq(plan), eq("build_" + BUILD_ID)))
                .thenReturn(snapshot());
        when(expectations.check(any(), any())).thenReturn(List.of());

        executor.execute(BUILD_ID);

        InOrder order = inOrder(previews, plans, writer);
        order.verify(previews).cancelForBuild(PIPELINE_ID);
        order.verify(previews).awaitReleased(eq(PIPELINE_ID), any(Duration.class));
        order.verify(plans).buildForBuild(eq(version), anyList(), eq(NODE_ID.toString()));
        order.verify(writer).write(eq(BUILD_ID), eq(OUTPUT_ID), eq(plan), eq("build_" + BUILD_ID));
        assertThat(row.getStatus()).isEqualTo(PipelineOutputStatus.SUCCEEDED);
        assertThat(row.getEngineJobId()).isEqualTo("engine-job-1");
        verify(publisher).publish(BUILD_ID);
    }

    @Test
    void doesNotExecuteWhenPreviewDoesNotRelease() {
        PipelineBuildEntity build = build();
        when(builds.findById(BUILD_ID)).thenReturn(Optional.of(build));
        when(previews.awaitReleased(eq(PIPELINE_ID), any(Duration.class))).thenReturn(false);

        executor.execute(BUILD_ID);

        assertThat(build.getStatus()).isEqualTo(PipelineBuildStatus.FAILED);
        assertThat(build.getErrorCode()).isEqualTo(PipelineBuildConstants.CODE_PREVIEW_BUSY);
        verifyNoInteractions(plans, writer, publisher);
    }

    @Test
    void releasePreviewExceptionFailsTheBuildWithoutEscaping() {
        PipelineBuildEntity build = build();
        when(builds.findById(BUILD_ID)).thenReturn(Optional.of(build));
        org.mockito.Mockito.doThrow(new IllegalStateException("preview registry unavailable"))
                .when(previews).cancelForBuild(PIPELINE_ID);

        executor.execute(BUILD_ID);

        assertThat(build.getStatus()).isEqualTo(PipelineBuildStatus.FAILED);
        assertThat(build.getErrorCode()).isEqualTo(PipelineBuildConstants.CODE_PREVIEW_BUSY);
        verifyNoInteractions(plans, writer, publisher);
    }

    @Test
    void usesExpectationsFrozenIntoThePipelineVersion() {
        PipelineBuildEntity build = build();
        ArrayNode rules = mapper.createArrayNode().add(mapper.createObjectNode()
                .put("type", "ROW_COUNT_RANGE").put("min", 1).put("max", 5));
        PipelineVersionEntity version = version(rules);
        PipelineBuildOutputEntity row = buildOutput();
        PipelineOutputEntity output = new PipelineOutputEntity(
                OUTPUT_ID, PIPELINE_ID, NODE_ID, PipelineOutputType.DATASET, "orders_out"
        );
        LogicalPlan plan = LogicalPlan.of(
                new PlanStep(NODE_ID.toString(), "dataset", new LogicalPlanNode.Union(), List.of()),
                version.getSourceDraftRevision(), NODE_ID.toString(), Map.of()
        );
        whenBuildReady(build, version, row, output);
        when(plans.buildForBuild(eq(version), anyList(), eq(NODE_ID.toString()))).thenReturn(plan);
        when(writer.write(eq(BUILD_ID), eq(OUTPUT_ID), eq(plan), eq("build_" + BUILD_ID)))
                .thenReturn(snapshot());
        when(expectations.check(any(), any())).thenReturn(List.of());

        executor.execute(BUILD_ID);

        ArgumentCaptor<ExpectationSet> captured = ArgumentCaptor.forClass(ExpectationSet.class);
        verify(expectations).check(captured.capture(), any());
        assertThat(captured.getValue().rules()).singleElement()
                .satisfies(rule -> assertThat(rule.path("type").asText())
                        .isEqualTo("ROW_COUNT_RANGE"));
    }

    @Test
    void failedOutputCleansItsTemporaryBranch() {
        PipelineBuildEntity build = build();
        PipelineVersionEntity version = version();
        PipelineBuildOutputEntity row = buildOutput();
        PipelineOutputEntity output = new PipelineOutputEntity(
                OUTPUT_ID, PIPELINE_ID, NODE_ID, PipelineOutputType.DATASET, "orders_out"
        );
        whenBuildReady(build, version, row, output);
        when(plans.buildForBuild(eq(version), anyList(), eq(NODE_ID.toString())))
                .thenThrow(new PipelineBuildException(
                        PipelineBuildConstants.CODE_EXECUTION_FAILED, "query failed"
                ));

        executor.execute(BUILD_ID);

        assertThat(build.getStatus()).isEqualTo(PipelineBuildStatus.FAILED);
        verify(branchCleaner).cleanup(BUILD_ID);
        verify(publisher, never()).publish(any());
    }

    private void whenBuildReady(
            PipelineBuildEntity build,
            PipelineVersionEntity version,
            PipelineBuildOutputEntity row,
            PipelineOutputEntity output
    ) {
        when(builds.findById(BUILD_ID)).thenReturn(Optional.of(build));
        when(versions.findById(VERSION_ID)).thenReturn(Optional.of(version));
        when(buildOutputs.findByBuildIdAndOutputId(BUILD_ID, OUTPUT_ID)).thenReturn(Optional.of(row));
        when(buildOutputs.findByBuildIdOrderByCreatedAtAsc(BUILD_ID)).thenReturn(List.of(row));
        when(outputs.findById(OUTPUT_ID)).thenReturn(Optional.of(output));
        when(previews.awaitReleased(eq(PIPELINE_ID), any(Duration.class))).thenReturn(true);
    }

    private PipelineBuildEntity build() {
        ObjectNode stats = mapper.createObjectNode();
        ResolvedInput input = new ResolvedInput(
                "sync", UUID.randomUUID(), UUID.randomUUID(), 4, "schema-hash",
                "ontoweave", "sales", "orders", 100L, null
        );
        return new PipelineBuildEntity(
                BUILD_ID, PIPELINE_ID, VERSION_ID, PipelineBuildStatus.QUEUED,
                PipelineBuildTrigger.MANUAL, UUID.randomUUID(), UUID.randomUUID(),
                PipelineResolvedInputs.toJson(mapper, List.of(input)), stats
        );
    }

    private PipelineVersionEntity version() {
        return version(mapper.createArrayNode());
    }

    private PipelineVersionEntity version(ArrayNode expectations) {
        ObjectNode output = mapper.createObjectNode()
                .put("outputId", OUTPUT_ID.toString())
                .put("nodeId", NODE_ID.toString())
                .put("outputType", "dataset")
                .put("name", "orders_out")
                .put("writeMode", "snapshot");
        output.putArray("primaryKeyColumns");
        output.set("expectations", expectations);
        ArrayNode outputs = mapper.createArrayNode().add(output);
        return new PipelineVersionEntity(
                VERSION_ID, PIPELINE_ID, 3, mapper.createObjectNode(), outputs,
                "definition-hash", mapper.createArrayNode(), 7L, UUID.randomUUID()
        );
    }

    private PipelineBuildOutputEntity buildOutput() {
        return new PipelineBuildOutputEntity(
                UUID.randomUUID(), BUILD_ID, OUTPUT_ID, PipelineOutputType.DATASET,
                PipelineOutputStatus.QUEUED, mapper.createObjectNode(), mapper.createObjectNode()
        );
    }

    private WrittenSnapshot snapshot() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "struct");
        schema.putArray("fields");
        return new WrittenSnapshot(
                DATASET_ID, "ontoweave", "pipeline_outputs", "orders_out",
                "build_" + BUILD_ID, 101L, schema, 2L, 128L, "/tmp/orders_out",
                Instant.now(), "engine-job-1"
        );
    }
}
