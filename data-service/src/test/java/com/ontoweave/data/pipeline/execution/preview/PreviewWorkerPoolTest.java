package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;
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
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.input;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.inputPlan;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.readyCheck;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.rows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreviewWorkerPoolTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    private final PipelineCheckService checks = mock(PipelineCheckService.class);
    private final PreviewPlanBuilder planBuilder = mock(PreviewPlanBuilder.class);
    private final FlinkTableExecutor executor = mock(FlinkTableExecutor.class);
    private final IcebergPreviewReader reader = mock(IcebergPreviewReader.class);
    private final ExecutorService callers = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() {
        callers.shutdownNow();
    }

    @Test
    void productionWorkerPoolIsBoundedAndIdleThreadsCanExpire() {
        ThreadPoolExecutor pool = PreviewWorkerPool.create();
        try {
            assertThat(pool.getMaximumPoolSize())
                    .isEqualTo(PipelineExecutionLimits.MAX_PREVIEW_WORKERS);
            assertThat(pool.getCorePoolSize()).isZero();
            assertThat(pool.getKeepAliveTime(TimeUnit.SECONDS)).isPositive();
            assertThat(pool.getQueue()).isInstanceOf(SynchronousQueue.class);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void returnsBusyWhenPreviewWorkerIsSaturated() throws Exception {
        CountDownLatch validationStarted = new CountDownLatch(1);
        CountDownLatch releaseValidation = new CountDownLatch(1);
        doAnswer(invocation -> {
            validationStarted.countDown();
            assertThat(releaseValidation.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(checks).validateRevision(PIPELINE_ID, OWNER_ID, 4L);
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.buildForPreview(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(inputPlan(NODE_ID, input, 4L));
        when(reader.read("ontoweave", "sales", "orders", 100L, 101))
                .thenReturn(Optional.of(new IcebergPreview(
                        List.of(new PreviewColumn("id", "long", false)), rows(1), false
                )));
        ThreadPoolExecutor oneWorker = new ThreadPoolExecutor(
                0, 1, 60, TimeUnit.SECONDS, new SynchronousQueue<>()
        );
        PreviewRegistry registry = new PreviewRegistry(Duration.ofSeconds(1));
        PipelinePreviewServiceImpl service = new PipelinePreviewServiceImpl(
                checks, planBuilder, executor, reader, Duration.ofSeconds(5), oneWorker, registry
        );
        try {
            Future<PreviewResponse> first = callers.submit(() -> service.preview(
                    PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
            ));
            assertThat(validationStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.preview(
                    PIPELINE_ID, OWNER_ID,
                    new PreviewRequest(4L, NODE_ID.toString(), UUID.randomUUID())
            )).isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(exception.code()).isEqualTo("PREVIEW_BUSY");
            });

            releaseValidation.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).rows()).hasSize(1);
            assertThat(registry.activeLeases(OWNER_ID)).isZero();
        } finally {
            releaseValidation.countDown();
            oneWorker.shutdownNow();
        }
    }
}
