package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.execution.flink.FlinkExecutionResult;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.flink.PipelineExecutionException;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import com.ontoweave.data.sync.iceberg.PreviewColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.BlockingExecutor;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.blockedCheck;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.input;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.inputPlan;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.readyCheck;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.rows;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.transformPlan;

class PipelinePreviewServiceTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    private final PipelineCheckService checks = mock(PipelineCheckService.class);
    private final PreviewPlanBuilder planBuilder = mock(PreviewPlanBuilder.class);
    private final FlinkTableExecutor executor = mock(FlinkTableExecutor.class);
    private final IcebergPreviewReader reader = mock(IcebergPreviewReader.class);
    private final ExecutorService workers = Executors.newCachedThreadPool();

    @AfterEach
    void shutdownWorkers() {
        workers.shutdownNow();
    }

    @Test
    void rejectsStaleDraftRevisionBeforeStartingExecution() {
        when(checks.check(PIPELINE_ID, OWNER_ID, 3L)).thenThrow(new PipelineRevisionConflict());
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        assertThatThrownBy(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(3L, NODE_ID.toString(), REQUEST_ID)
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.code()).isEqualTo("REVISION_CONFLICT");
        });

        verifyNoInteractions(planBuilder, executor, reader);
    }

    @Test
    void rejectsBlockingCheckWithoutStartingFlink() {
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(blockedCheck(PIPELINE_ID, 4L, NODE_ID));
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        assertThatThrownBy(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(exception.code()).isEqualTo("PREVIEW_BLOCKED");
        });

        verifyNoInteractions(planBuilder, executor, reader);
    }

    @Test
    void inputNodeUsesDirectIcebergReaderAndNeverStartsFlink() {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input, 4L));
        when(reader.read("ontoweave", "sales", "orders", 100L, 501))
                .thenReturn(Optional.of(new IcebergPreview(
                        List.of(new com.ontoweave.data.sync.iceberg.PreviewColumn("id", "long", false)),
                        rows(501),
                        true
                )));
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        PreviewResponse response = service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        );

        assertThat(response.previewId()).isEqualTo(REQUEST_ID);
        assertThat(response.rows()).hasSize(500);
        assertThat(response.returnedRows()).isEqualTo(500);
        assertThat(response.truncated()).isTrue();
        assertThat(response.truncationReason()).isEqualTo("OUTPUT_ROW_LIMIT");
        assertThat(response.inputVersions()).singleElement();
        verifyNoInteractions(executor);
    }

    @Test
    void transformNodeUsesFlinkAndStripsExecutorIssues() {
        ResolvedInput input = input(100L);
        PreviewIssue issue = new PreviewIssue(
                "TYPE_MISMATCH", "warning", NODE_ID.toString(), "amount", 2L,
                "值无法转换为 long", "abc"
        );
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(500),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(new FlinkExecutionResult(
                        UUID.randomUUID(),
                        List.of(new PreviewColumn("id", "long", false)),
                        rows(501),
                        12_000L,
                        false,
                        List.of(),
                        List.of(issue)
                ));
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        PreviewResponse response = service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        );

        assertThat(response.rows()).hasSize(500);
        assertThat(response.truncated()).isTrue();
        assertThat(response.scannedRows()).isEqualTo(12_000L);
        assertThat(response.warnings()).containsExactly(issue);
    }

    @Test
    void marksScanRowLimitWhenAnyInputExceedsScanLimit() {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(500),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(new FlinkExecutionResult(
                        UUID.randomUUID(),
                        List.of(new com.ontoweave.data.sync.iceberg.PreviewColumn("id", "long", false)),
                        rows(10),
                        50_001L,
                        true,
                        List.of(),
                        List.of()
                ));
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        PreviewResponse response = service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        );

        assertThat(response.truncated()).isTrue();
        assertThat(response.truncationReason()).isEqualTo("SCAN_ROW_LIMIT");
        assertThat(response.scannedRows()).isEqualTo(50_001L);
    }

    @Test
    void doesNotMarkScanRowLimitWhenInputsStayWithinLimit() {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(500),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(new FlinkExecutionResult(
                        UUID.randomUUID(),
                        List.of(new com.ontoweave.data.sync.iceberg.PreviewColumn("id", "long", false)),
                        rows(10),
                        50_000L,
                        false,
                        List.of(),
                        List.of()
                ));
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        PreviewResponse response = service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        );

        assertThat(response.truncated()).isFalse();
        assertThat(response.truncationReason()).isNull();
        assertThat(response.scannedRows()).isEqualTo(50_000L);
    }

    @Test
    void mapsExecutionTimeoutToPreviewTimeout() {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        BlockingExecutor blocking = new BlockingExecutor();
        PipelinePreviewServiceImpl service = new PipelinePreviewServiceImpl(
                checks, planBuilder, blocking, reader, Duration.ofMillis(30), workers
        );

        assertThatThrownBy(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
            assertThat(exception.code()).isEqualTo("PREVIEW_TIMEOUT");
        });
    }

    @Test
    void mapsExecutionFailureToNodeAndInputPortWithoutInventingField() {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        when(executor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(500),
                org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenThrow(new PipelineExecutionException(
                        "bad expression", NODE_ID.toString(), "in", null, new RuntimeException("bad expression")
                ));
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        assertThatThrownBy(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(exception.code()).isEqualTo("PREVIEW_EXECUTION_FAILED");
            assertThat(exception.details())
                    .containsEntry("nodeId", NODE_ID.toString())
                    .containsEntry("portId", "in")
                    .doesNotContainKey("field");
        });
    }

    @Test
    void buildPreemptionCancelsRunningPreview() throws Exception {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        BlockingExecutor blocking = new BlockingExecutor();
        PipelinePreviewServiceImpl service = new PipelinePreviewServiceImpl(
                checks, planBuilder, blocking, reader, Duration.ofSeconds(5), workers
        );
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            Future<?> preview = callers.submit(() -> service.preview(
                    PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
            ));
            assertThat(blocking.awaitStarted(1, TimeUnit.SECONDS)).isTrue();

            service.cancelForBuild(PIPELINE_ID);

            assertThatThrownBy(() -> preview.get(2, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(ApiException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(ApiException.class, exception ->
                            assertThat(exception.code()).isEqualTo("PREVIEW_CANCELED"));
        } finally {
            callers.shutdownNow();
        }
    }

    private PipelinePreviewService service(Duration timeout) {
        return new PipelinePreviewServiceImpl(checks, planBuilder, executor, reader, timeout, workers);
    }
}
