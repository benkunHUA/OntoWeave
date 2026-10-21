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

import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.input;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.inputPlan;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.readyCheck;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.rows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinePreviewTimeoutTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    private final PipelineCheckService checks = mock(PipelineCheckService.class);
    private final PreviewPlanBuilder planBuilder = mock(PreviewPlanBuilder.class);
    private final FlinkTableExecutor executor = mock(FlinkTableExecutor.class);
    private final IcebergPreviewReader reader = mock(IcebergPreviewReader.class);
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ExecutorService callers = Executors.newCachedThreadPool();
    private final PreviewRegistry registry = new PreviewRegistry(Duration.ofSeconds(1));

    @AfterEach
    void shutdown() {
        workers.shutdownNow();
        callers.shutdownNow();
    }

    @Test
    void blockingCheckTimesOutAndReleasesTheLease() throws Exception {
        CountDownLatch firstCheckStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCheck = new CountDownLatch(1);
        AtomicInteger checkCalls = new AtomicInteger();
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenAnswer(invocation -> {
            if (checkCalls.getAndIncrement() == 0) {
                firstCheckStarted.countDown();
                awaitIgnoringInterruptions(releaseFirstCheck);
            }
            return readyCheck(PIPELINE_ID, 4L);
        });
        prepareInputPreview();
        PipelinePreviewService service = service(Duration.ofMillis(100));

        try {
            assertTimeoutReleasesLease(service, firstCheckStarted);
            assertThat(service.preview(PIPELINE_ID, OWNER_ID, request()).rows()).hasSize(1);
        } finally {
            releaseFirstCheck.countDown();
        }
    }

    @Test
    void blockingPlanBuildTimesOutAndReleasesTheLease() throws Exception {
        CountDownLatch firstPlanStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstPlan = new CountDownLatch(1);
        AtomicInteger planCalls = new AtomicInteger();
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString())).thenAnswer(invocation -> {
            if (planCalls.getAndIncrement() == 0) {
                firstPlanStarted.countDown();
                awaitIgnoringInterruptions(releaseFirstPlan);
            }
            return inputPlan(NODE_ID, input(100L), 4L);
        });
        prepareReader();
        PipelinePreviewService service = service(Duration.ofMillis(100));

        try {
            assertTimeoutReleasesLease(service, firstPlanStarted);
            assertThat(service.preview(PIPELINE_ID, OWNER_ID, request()).rows()).hasSize(1);
        } finally {
            releaseFirstPlan.countDown();
        }
    }

    @Test
    void readerThatIgnoresInterruptsCannotHoldTheRequestOrLease() throws Exception {
        CountDownLatch firstReadStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRead = new CountDownLatch(1);
        AtomicInteger readCalls = new AtomicInteger();
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input(100L), 4L));
        when(reader.read("ontoweave", "sales", "orders", 100L, 501)).thenAnswer(invocation -> {
            if (readCalls.getAndIncrement() == 0) {
                firstReadStarted.countDown();
                awaitIgnoringInterruptions(releaseFirstRead);
            }
            return Optional.of(preview());
        });
        PipelinePreviewService service = service(Duration.ofMillis(100));

        try {
            assertTimeoutReleasesLease(service, firstReadStarted);
            assertThat(registry.awaitReleased(PIPELINE_ID, Duration.ofMillis(50))).isFalse();
            assertThat(service.preview(PIPELINE_ID, OWNER_ID, request()).rows()).hasSize(1);
        } finally {
            releaseFirstRead.countDown();
        }
        assertThat(registry.awaitReleased(PIPELINE_ID, Duration.ofSeconds(1))).isTrue();
    }

    private void assertTimeoutReleasesLease(
            PipelinePreviewService service,
            CountDownLatch firstPhaseStarted
    ) throws Exception {
        Future<?> first = callers.submit(() -> service.preview(PIPELINE_ID, OWNER_ID, request()));
        assertThat(firstPhaseStarted.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> first.get(1, TimeUnit.SECONDS))
                .hasRootCauseInstanceOf(ApiException.class)
                .rootCause()
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(504);
                    assertThat(exception.code()).isEqualTo("PREVIEW_TIMEOUT");
                });
        assertThat(registry.hasActiveLease(PIPELINE_ID)).isFalse();
        assertThat(registry.activeLeases(OWNER_ID)).isZero();
    }

    private void prepareInputPreview() {
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input(100L), 4L));
        prepareReader();
    }

    private void prepareReader() {
        when(reader.read("ontoweave", "sales", "orders", 100L, 501))
                .thenReturn(Optional.of(preview()));
    }

    private IcebergPreview preview() {
        return new IcebergPreview(
                List.of(new PreviewColumn("id", "long", false)), rows(1), false
        );
    }

    private PipelinePreviewService service(Duration timeout) {
        return new PipelinePreviewServiceImpl(
                checks, planBuilder, executor, reader, timeout, workers, registry
        );
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
