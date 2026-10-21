package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
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

import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.input;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.inputPlan;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.readyCheck;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.rows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinePreviewBuildCancellationTest {

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
    void buildCancellationKeepsIgnoreInterruptReaderDrainingWithoutBlockingNextPreview()
            throws Exception {
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input(100L), 4L));
        when(reader.read("ontoweave", "sales", "orders", 100L, 501)).thenAnswer(invocation -> {
            if (reads.getAndIncrement() == 0) {
                readStarted.countDown();
                awaitIgnoringInterruptions(releaseRead);
            }
            return Optional.of(preview());
        });
        PreviewRegistry registry = new PreviewRegistry(Duration.ofMillis(50));
        PipelinePreviewServiceImpl service = new PipelinePreviewServiceImpl(
                checks, planBuilder, mock(FlinkTableExecutor.class),
                reader, Duration.ofSeconds(5), workers, registry
        );

        Future<?> old = callers.submit(() -> service.preview(PIPELINE_ID, OWNER_ID, request()));
        try {
            assertThat(readStarted.await(1, TimeUnit.SECONDS)).isTrue();
            service.cancelForBuild(PIPELINE_ID);

            assertThatThrownBy(() -> old.get(1, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(ApiException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(ApiException.class, exception ->
                            assertThat(exception.code()).isEqualTo("PREVIEW_CANCELED"));
            assertThat(service.awaitReleased(PIPELINE_ID, Duration.ofMillis(100))).isFalse();

            PreviewResponse replacement = service.preview(PIPELINE_ID, OWNER_ID, request());
            assertThat(replacement.rows()).hasSize(1);
        } finally {
            releaseRead.countDown();
        }
        assertThat(service.awaitReleased(PIPELINE_ID, Duration.ofSeconds(1))).isTrue();
    }

    private IcebergPreview preview() {
        return new IcebergPreview(List.of(new PreviewColumn("id", "long", false)), rows(1), false);
    }

    private PreviewRequest request() {
        return new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID());
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
