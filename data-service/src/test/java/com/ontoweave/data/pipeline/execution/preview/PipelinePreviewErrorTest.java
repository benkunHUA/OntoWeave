package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.flink.PipelineExecutionException;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.input;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.readyCheck;
import static com.ontoweave.data.pipeline.execution.preview.PipelinePreviewTestSupport.transformPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinePreviewErrorTest {

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
    void shutdown() {
        workers.shutdownNow();
    }

    @Test
    void mapsExecutionFailureToNodeAndInputPortWithoutInventingField() {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        when(executor.execute(any(), eq(500), any(Duration.class)))
                .thenThrow(new PipelineExecutionException(
                        "bad expression", NODE_ID.toString(), "in", null, new RuntimeException("bad expression")
                ));
        PipelinePreviewService service = service();

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
    void unknownExecutionFailureDoesNotLeakTheUnderlyingMessage() {
        ResolvedInput input = input(100L);
        when(checks.check(PIPELINE_ID, OWNER_ID, 4L)).thenReturn(readyCheck(PIPELINE_ID, 4L));
        when(planBuilder.build(PIPELINE_ID, OWNER_ID, 4L, NODE_ID.toString()))
                .thenReturn(transformPlan(NODE_ID, input, 4L));
        when(executor.execute(any(), eq(500), any(Duration.class)))
                .thenThrow(new IllegalStateException("password=secret"));
        PipelinePreviewService service = service();

        assertThatThrownBy(() -> service.preview(
                PIPELINE_ID, OWNER_ID, new PreviewRequest(4L, NODE_ID.toString(), REQUEST_ID)
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(exception.code()).isEqualTo("INTERNAL_ERROR");
            assertThat(exception.getMessage()).doesNotContain("secret");
        });
    }

    private PipelinePreviewService service() {
        return new PipelinePreviewServiceImpl(
                checks, planBuilder, executor, reader, Duration.ofSeconds(1), workers
        );
    }
}
