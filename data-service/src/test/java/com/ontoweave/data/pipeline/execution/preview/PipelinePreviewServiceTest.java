package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.execution.flink.FlinkExecutionResult;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
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
import java.util.concurrent.atomic.AtomicReference;

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
        org.mockito.Mockito.doThrow(new PipelineRevisionConflict())
                .when(checks).validateRevision(PIPELINE_ID, OWNER_ID, 3L);
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
                "TYPE_MISMATCH", "execution", "warning", NODE_ID.toString(), "in",
                "amount", 2L, "值无法转换为 long", "检查原始值格式", "abc"
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
                        null,
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
        assertThat(response.scannedRows()).isNull();
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
                        null,
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
        assertThat(response.scannedRows()).isNull();
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
                        null,
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
        assertThat(response.scannedRows()).isNull();
    }

    @Test
    void mapsCheckThatExhaustsBudgetToPreviewTimeout() {
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenAnswer(invocation -> {
            Thread.sleep(80);
            return readyCheck(PIPELINE_ID, 4L);
        });
        PipelinePreviewService service = service(Duration.ofMillis(30));

        assertThatThrownBy(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo("PREVIEW_TIMEOUT"));

        verifyNoInteractions(planBuilder, executor, reader);
    }

    @Test
    void mapsPlanBuildThatExhaustsBudgetToPreviewTimeout() {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString())).thenAnswer(invocation -> {
            Thread.sleep(80);
            return transformPlan(NODE_ID, input, 4L);
        });
        PipelinePreviewService service = service(Duration.ofMillis(30));

        assertThatThrownBy(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo("PREVIEW_TIMEOUT"));

        verifyNoInteractions(executor, reader);
    }

    @Test
    void passesOnlyRemainingBudgetToFlink() {
        ResolvedInput input = input(100L);
        AtomicReference<Duration> executionBudget = new AtomicReference<>();
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenAnswer(invocation -> {
            Thread.sleep(150);
            return readyCheck(PIPELINE_ID, 4L);
        });
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        when(executor.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(500),
                org.mockito.ArgumentMatchers.any(Duration.class)
        )).thenAnswer(invocation -> {
            executionBudget.set(invocation.getArgument(2));
            return new FlinkExecutionResult(
                    UUID.randomUUID(),
                    List.of(new PreviewColumn("id", "long", false)),
                    rows(1),
                    null,
                    false,
                    List.of(),
                    List.of()
            );
        });
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        );

        assertThat(executionBudget.get()).isPositive().isLessThan(Duration.ofSeconds(1));
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

    private PipelinePreviewService service(Duration timeout) {
        return new PipelinePreviewServiceImpl(checks, planBuilder, executor, reader, timeout, workers);
    }
}
