package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import com.ontoweave.data.sync.iceberg.PreviewColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.BlockingOnceExecutor;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.input;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.inputPlan;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.readyCheck;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.rows;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.transformPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinePreviewCancellationTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    private final PipelineCheckService checks = mock(PipelineCheckService.class);
    private final PreviewPlanBuilder planBuilder = mock(PreviewPlanBuilder.class);
    private final IcebergPreviewReader reader = mock(IcebergPreviewReader.class);
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ExecutorService callers = Executors.newCachedThreadPool();

    @AfterEach
    void shutdown() {
        workers.shutdownNow();
        callers.shutdownNow();
    }

    @Test
    void newPreviewCancelsRunningOldPreviewAndThenContinues() throws Exception {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        BlockingOnceExecutor executor = new BlockingOnceExecutor();
        PreviewRegistry registry = new PreviewRegistry(Duration.ofSeconds(1));
        PipelinePreviewServiceImpl service = new PipelinePreviewServiceImpl(
                checks, planBuilder, executor, reader, Duration.ofSeconds(5), workers, registry
        );

        Future<?> old = callers.submit(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID())
        ));
        assertThat(executor.awaitStarted(1, TimeUnit.SECONDS)).isTrue();

        Future<PreviewResponse> replacement = callers.submit(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID())
        ));

        assertThatThrownBy(() -> old.get(2, TimeUnit.SECONDS))
                .hasRootCauseInstanceOf(ApiException.class)
                .rootCause()
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("PREVIEW_CANCELED"));
        assertThat(replacement.get(2, TimeUnit.SECONDS).rows()).hasSize(1);
    }

    @Test
    void newPreviewCanPreemptOldPreviewDuringChecks() throws Exception {
        CountDownLatch firstCheckStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCheck = new CountDownLatch(1);
        AtomicInteger checkCalls = new AtomicInteger();
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenAnswer(invocation -> {
            if (checkCalls.getAndIncrement() == 0) {
                firstCheckStarted.countDown();
                assertThat(releaseFirstCheck.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return readyCheck(PIPELINE_ID, 4L);
        });
        ResolvedInput input = input(100L);
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input, 4L));
        when(reader.read("ontoweave", "sales", "orders", 100L, 501))
                .thenReturn(Optional.of(new IcebergPreview(
                        List.of(new PreviewColumn("id", "long", false)),
                        rows(1),
                        false
                )));
        PreviewRegistry registry = new PreviewRegistry(Duration.ofSeconds(1));
        PipelinePreviewServiceImpl service = new PipelinePreviewServiceImpl(
                checks, planBuilder, mock(FlinkTableExecutor.class),
                reader, Duration.ofSeconds(5), workers, registry
        );

        Future<?> old = callers.submit(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID())
        ));
        assertThat(firstCheckStarted.await(1, TimeUnit.SECONDS)).isTrue();

        Future<PreviewResponse> replacement = callers.submit(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID())
        ));
        assertThat(registry.awaitCancellationRequested(PIPELINE_ID, Duration.ofSeconds(1))).isTrue();
        releaseFirstCheck.countDown();

        assertThatThrownBy(() -> old.get(2, TimeUnit.SECONDS))
                .hasRootCauseInstanceOf(ApiException.class)
                .rootCause()
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("PREVIEW_CANCELED"));
        assertThat(replacement.get(2, TimeUnit.SECONDS).rows()).hasSize(1);
    }

    @Test
    void slowOldValidationCannotCancelPreviewRegisteredWithHigherEpoch() throws Exception {
        CountDownLatch oldValidationStarted = new CountDownLatch(1);
        CountDownLatch releaseOldValidation = new CountDownLatch(1);
        CountDownLatch newCheckStarted = new CountDownLatch(1);
        CountDownLatch releaseNewCheck = new CountDownLatch(1);
        AtomicInteger validationCalls = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (validationCalls.getAndIncrement() == 0) {
                oldValidationStarted.countDown();
                assertThat(releaseOldValidation.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(checks).validateRevision(PIPELINE_ID, OWNER_ID, 4L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenAnswer(invocation -> {
            newCheckStarted.countDown();
            assertThat(releaseNewCheck.await(2, TimeUnit.SECONDS)).isTrue();
            return readyCheck(PIPELINE_ID, 4L);
        });
        ResolvedInput input = input(100L);
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input, 4L));
        when(reader.read("ontoweave", "sales", "orders", 100L, 501))
                .thenReturn(Optional.of(new IcebergPreview(
                        List.of(new PreviewColumn("id", "long", false)),
                        rows(1),
                        false
                )));
        PreviewRegistry registry = new PreviewRegistry(Duration.ofSeconds(1));
        PipelinePreviewServiceImpl service = new PipelinePreviewServiceImpl(
                checks, planBuilder, mock(FlinkTableExecutor.class),
                reader, Duration.ofSeconds(5), workers, registry
        );

        Future<PreviewResponse> old = callers.submit(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID())
        ));
        assertThat(oldValidationStarted.await(1, TimeUnit.SECONDS)).isTrue();

        Future<PreviewResponse> current = callers.submit(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID())
        ));
        assertThat(newCheckStarted.await(1, TimeUnit.SECONDS)).isTrue();

        releaseOldValidation.countDown();
        assertThatThrownBy(() -> old.get(2, TimeUnit.SECONDS))
                .hasRootCauseInstanceOf(ApiException.class)
                .rootCause()
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("PREVIEW_CANCELED"));
        assertThat(current).isNotDone();
        assertThat(registry.awaitCancellationRequested(PIPELINE_ID, Duration.ofMillis(100))).isFalse();

        releaseNewCheck.countDown();
        assertThat(current.get(2, TimeUnit.SECONDS).rows()).hasSize(1);
    }

    @Test
    void staleRequestDoesNotCancelCurrentPreview() throws Exception {
        CountDownLatch checkStarted = new CountDownLatch(1);
        CountDownLatch releaseCheck = new CountDownLatch(1);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenAnswer(invocation -> {
            checkStarted.countDown();
            assertThat(releaseCheck.await(2, TimeUnit.SECONDS)).isTrue();
            return readyCheck(PIPELINE_ID, 4L);
        });
        org.mockito.Mockito.doThrow(new com.ontoweave.data.pipeline.draft.PipelineRevisionConflict())
                .when(checks).validateRevision(PIPELINE_ID, OWNER_ID, 3L);
        ResolvedInput input = input(100L);
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input, 4L));
        when(reader.read("ontoweave", "sales", "orders", 100L, 501))
                .thenReturn(Optional.of(new IcebergPreview(
                        List.of(new PreviewColumn("id", "long", false)),
                        rows(1),
                        false
                )));
        PreviewRegistry registry = new PreviewRegistry(Duration.ofSeconds(1));
        PipelinePreviewServiceImpl service = new PipelinePreviewServiceImpl(
                checks, planBuilder, mock(FlinkTableExecutor.class),
                reader, Duration.ofSeconds(5), workers, registry
        );

        Future<PreviewResponse> current = callers.submit(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID())
        ));
        assertThat(checkStarted.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(3L, NODE_ID.toString(), UUID.randomUUID())
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo("REVISION_CONFLICT"));

        assertThat(current).isNotDone();
        assertThat(registry.awaitCancellationRequested(PIPELINE_ID, Duration.ofMillis(100))).isFalse();
        releaseCheck.countDown();
        assertThat(current.get(2, TimeUnit.SECONDS).rows()).hasSize(1);
    }
}
