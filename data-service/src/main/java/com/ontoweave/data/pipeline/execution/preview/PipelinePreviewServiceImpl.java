package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
public class PipelinePreviewServiceImpl implements PipelinePreviewService {

    private final PreviewStageExecutor stages;
    private final PreviewRegistry registry;
    private final PreviewExecutionEngine execution;

    @Autowired
    public PipelinePreviewServiceImpl(
            PipelineCheckService checks,
            PreviewPlanBuilder planBuilder,
            FlinkTableExecutor executor,
            IcebergPreviewReader reader
    ) {
        this(
                checks,
                planBuilder,
                executor,
                reader,
                PipelineExecutionLimits.PREVIEW_TIMEOUT,
                PreviewWorkerPool.create(),
                true,
                new PreviewRegistry()
        );
    }

    PipelinePreviewServiceImpl(
            PipelineCheckService checks,
            PreviewPlanBuilder planBuilder,
            FlinkTableExecutor executor,
            IcebergPreviewReader reader,
            Duration timeout,
            ExecutorService workers
    ) {
        this(checks, planBuilder, executor, reader, timeout, workers, false, new PreviewRegistry());
    }

    PipelinePreviewServiceImpl(
            PipelineCheckService checks,
            PreviewPlanBuilder planBuilder,
            FlinkTableExecutor executor,
            IcebergPreviewReader reader,
            Duration timeout,
            ExecutorService workers,
            PreviewRegistry registry
    ) {
        this(checks, planBuilder, executor, reader, timeout, workers, false, registry);
    }

    private PipelinePreviewServiceImpl(
            PipelineCheckService checks,
            PreviewPlanBuilder planBuilder,
            FlinkTableExecutor executor,
            IcebergPreviewReader reader,
            Duration timeout,
            ExecutorService workers,
            boolean ownsWorkers,
            PreviewRegistry registry
    ) {
        this.stages = new PreviewStageExecutor(checks, planBuilder, executor, reader);
        this.registry = registry;
        this.execution = new PreviewExecutionEngine(stages, timeout, workers, ownsWorkers, registry);
    }

    @PreDestroy
    void shutdownWorkers() {
        execution.shutdown();
    }

    @Override
    public PreviewResponse preview(UUID pipelineId, UUID ownerId, PreviewRequest request) {
        return execution.preview(pipelineId, ownerId, request);
    }

    @Override
    public void cancel(UUID pipelineId, UUID ownerId) {
        stages.assertOwned(pipelineId, ownerId);
        registry.cancelForBuild(pipelineId);
    }

    @Override
    public void cancelForBuild(UUID pipelineId) {
        registry.cancelForBuild(pipelineId);
    }

    @Override
    public boolean awaitReleased(UUID pipelineId, Duration timeout) {
        return registry.awaitReleased(pipelineId, timeout);
    }
}
