package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.check.PipelineCheckResult;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.execution.flink.FlinkExecutionResult;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PreviewStageExecutor {

    private final PipelineCheckService checks;
    private final PreviewPlanBuilder planBuilder;
    private final FlinkTableExecutor executor;
    private final IcebergPreviewReader reader;

    PreviewStageExecutor(
            PipelineCheckService checks,
            PreviewPlanBuilder planBuilder,
            FlinkTableExecutor executor,
            IcebergPreviewReader reader
    ) {
        this.checks = checks;
        this.planBuilder = planBuilder;
        this.executor = executor;
        this.reader = reader;
    }

    void validateRevision(UUID pipelineId, UUID ownerId, long revision) {
        checks.validateRevision(pipelineId, ownerId, revision);
    }

    void assertOwned(UUID pipelineId, UUID ownerId) {
        checks.assertOwned(pipelineId, ownerId);
    }

    PreviewComputation compute(
            UUID pipelineId,
            UUID ownerId,
            PreviewRequest request,
            PreviewLease lease,
            PreviewDeadline deadline
    ) {
        ensureActive(lease);
        ensureBudget(deadline);
        PipelineCheckResult check = checks.check(pipelineId, ownerId, request.draftRevision());
        ensureActive(lease);
        ensureBudget(deadline);
        if (!check.blockingIssues().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREVIEW_BLOCKED",
                    "流程检查存在阻塞错误，不能预览",
                    Map.of("blockingIssues", check.blockingIssues())
            );
        }
        LogicalPlan plan = planBuilder.build(
                pipelineId, ownerId, request.draftRevision(), request.nodeId()
        );
        ensureActive(lease);
        ensureBudget(deadline);
        PreviewResponseMapper.ExecutionResult result = execute(plan, remaining(deadline));
        ensureActive(lease);
        ensureBudget(deadline);
        return new PreviewComputation(plan, result);
    }

    private PreviewResponseMapper.ExecutionResult execute(
            LogicalPlan plan,
            Duration executionBudget
    ) {
        if (plan.root().node() instanceof LogicalPlanNode.ScanInput scan) {
            IcebergPreview preview = reader.read(
                    scan.catalog(),
                    scan.namespace(),
                    scan.table(),
                    scan.snapshotId(),
                    PipelineExecutionLimits.READ_ROW_LIMIT
            ).orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "INPUT_PREVIEW_UNAVAILABLE",
                    "输入版本对应的数据文件不存在或已被清理"
            ));
            return new PreviewResponseMapper.ExecutionResult(
                    preview.columns(),
                    preview.rows(),
                    null,
                    false,
                    preview.truncated(),
                    List.of(),
                    List.of()
            );
        }
        FlinkExecutionResult result = executor.execute(
                plan, PipelineExecutionLimits.OUTPUT_ROW_LIMIT, executionBudget
        );
        return new PreviewResponseMapper.ExecutionResult(
                result.columns(),
                result.rows(),
                result.scannedRows(),
                result.scanLimited(),
                false,
                result.errors(),
                result.warnings()
        );
    }

    private void ensureActive(PreviewLease lease) {
        if (lease.isStopped()) {
            throw PreviewErrorMapper.canceled();
        }
    }

    private void ensureBudget(PreviewDeadline deadline) {
        if (deadline.expired()) {
            throw PreviewErrorMapper.timeout();
        }
    }

    private Duration remaining(PreviewDeadline deadline) {
        Duration remaining = deadline.remaining();
        if (remaining.isZero() || remaining.isNegative()) {
            throw PreviewErrorMapper.timeout();
        }
        return remaining;
    }

    record PreviewComputation(
            LogicalPlan plan,
            PreviewResponseMapper.ExecutionResult result
    ) {
    }
}
