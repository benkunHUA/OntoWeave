package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.input;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.inputPlan;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.readyCheck;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.rows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinePreviewEpochTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    private final PipelineCheckService checks = mock(PipelineCheckService.class);
    private final PreviewPlanBuilder planBuilder = mock(PreviewPlanBuilder.class);
    private final IcebergPreviewReader reader = mock(IcebergPreviewReader.class);
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final PreviewRegistry registry = new PreviewRegistry();

    @AfterEach
    void shutdownWorkers() {
        workers.shutdownNow();
    }

    @Test
    void randomUnauthorizedPipelinesDoNotCreateEpochState() {
        doThrow(notFound()).when(checks).assertOwned(any(UUID.class), eq(OWNER_ID));
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        for (int index = 0; index < 20; index++) {
            UUID pipelineId = UUID.randomUUID();
            assertThatThrownBy(() -> service.preview(pipelineId, OWNER_ID, request()))
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(exception.code()).isEqualTo("NOT_FOUND");
                    });
        }

        assertThat(registry.epochStateCount()).isZero();
    }

    @Test
    void closesPendingEpochAfterSuccessAndKnownFailure() {
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input(100L), 4L));
        when(reader.read("ontoweave", "sales", "orders", 100L, 501))
                .thenReturn(Optional.of(new IcebergPreview(
                        List.of(new PreviewColumn("id", "long", false)), rows(1), false
                )));
        PipelinePreviewService service = service(Duration.ofSeconds(1));

        service.preview(PIPELINE_ID, OWNER_ID, request());
        assertThat(registry.pendingRequests(PIPELINE_ID)).isZero();

        doThrow(new PipelineRevisionConflict())
                .when(checks).validateRevision(PIPELINE_ID, OWNER_ID, 3L);
        assertThatThrownBy(() -> service.preview(PIPELINE_ID, OWNER_ID, new PreviewRequest(
                3L, NODE_ID.toString(), UUID.randomUUID()
        ))).isInstanceOf(PipelineRevisionConflict.class);
        assertThat(registry.pendingRequests(PIPELINE_ID)).isZero();
    }

    @Test
    void closesPendingEpochAfterTimeout() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            awaitIgnoringInterruptions(release);
            return null;
        }).when(checks).validateRevision(PIPELINE_ID, OWNER_ID, 4L);
        PipelinePreviewService service = service(Duration.ofMillis(50));

        try {
            assertThatThrownBy(() -> service.preview(PIPELINE_ID, OWNER_ID, request()))
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                        assertThat(exception.code()).isEqualTo("PREVIEW_TIMEOUT");
                    });
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.pendingRequests(PIPELINE_ID)).isZero();
        } finally {
            release.countDown();
        }
    }

    private PipelinePreviewService service(Duration timeout) {
        return new PipelinePreviewServiceImpl(
                checks, planBuilder, mock(FlinkTableExecutor.class), reader,
                timeout, workers, registry
        );
    }

    private PreviewRequest request() {
        return new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID());
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "流程不存在");
    }

    private static void awaitIgnoringInterruptions(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException error) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
