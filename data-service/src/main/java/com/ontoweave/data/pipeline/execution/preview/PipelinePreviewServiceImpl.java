package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.check.PipelineCheckResult;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.execution.flink.FlinkExecutionResult;
import com.ontoweave.data.pipeline.execution.flink.FlinkPreviewTimeoutException;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.flink.PipelineExecutionException;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import com.ontoweave.data.sync.iceberg.PreviewColumn;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class PipelinePreviewServiceImpl implements PipelinePreviewService {

    private final PipelineCheckService checks;
    private final PreviewPlanBuilder planBuilder;
    private final FlinkTableExecutor executor;
    private final IcebergPreviewReader reader;
    private final Duration timeout;
    private final ExecutorService workers;
    private final boolean ownsWorkers;
    private final PreviewRegistry registry;

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
                Executors.newCachedThreadPool(),
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
        this.checks = checks;
        this.planBuilder = planBuilder;
        this.executor = executor;
        this.reader = reader;
        this.timeout = timeout;
        this.workers = workers;
        this.ownsWorkers = ownsWorkers;
        this.registry = registry;
    }

    @PreDestroy
    void shutdownWorkers() {
        if (ownsWorkers) {
            workers.shutdownNow();
        }
    }

    @Override
    public PreviewResponse preview(UUID pipelineId, UUID ownerId, PreviewRequest request) {
        PreviewDeadline deadline = PreviewDeadline.start(timeout);
        validateRevision(pipelineId, ownerId, request.draftRevision(), deadline);
        ensureBudget(deadline);
        PreviewRegistry.Lease lease;
        try {
            lease = registry.register(pipelineId, ownerId, deadline.remaining());
        } catch (PreviewRegistry.BudgetExceededException error) {
            throw timeout();
        }
        Future<PreviewComputation> future = null;
        try {
            ensureActive(lease);
            ensureBudget(deadline);
            future = workers.submit(() -> compute(pipelineId, ownerId, request, lease, deadline));
            lease.attach(future);
            PreviewComputation computation = await(future, lease, deadline);
            ensureActive(lease);
            ensureBudget(deadline);
            return response(
                    pipelineId,
                    request,
                    computation.plan(),
                    computation.result(),
                    deadline.elapsedMs()
            );
        } catch (ApiException error) {
            if (lease.isCanceled()) {
                throw canceled();
            }
            throw error;
        } catch (RuntimeException error) {
            if (lease.isCanceled()) {
                throw canceled();
            }
            throw error;
        } finally {
            lease.detach();
            registry.unregister(pipelineId, ownerId, lease);
        }
    }

    @Override
    public void cancelForBuild(UUID pipelineId) {
        registry.cancelForBuild(pipelineId);
    }

    private void validateRevision(
            UUID pipelineId,
            UUID ownerId,
            long revision,
            PreviewDeadline deadline
    ) {
        Duration remaining = remaining(deadline);
        Future<?> future = workers.submit(() -> {
            checks.validateRevision(pipelineId, ownerId, revision);
            return null;
        });
        try {
            future.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw timeout();
        } catch (CancellationException error) {
            throw timeout();
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw timeout();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            throw executionFailure(cause);
        }
    }

    private PreviewComputation compute(
            UUID pipelineId,
            UUID ownerId,
            PreviewRequest request,
            PreviewRegistry.Lease lease,
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
        ExecutionResult result = execute(plan, remaining(deadline));
        ensureActive(lease);
        ensureBudget(deadline);
        return new PreviewComputation(plan, result);
    }

    private ExecutionResult execute(LogicalPlan plan, Duration executionBudget) {
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
            return new ExecutionResult(
                    preview.columns(),
                    preview.rows(),
                    null,
                    false,
                    List.of(),
                    List.of()
            );
        }
        FlinkExecutionResult result = executor.execute(
                plan, PipelineExecutionLimits.OUTPUT_ROW_LIMIT, executionBudget
        );
        return new ExecutionResult(
                result.columns(),
                result.rows(),
                result.scannedRows(),
                result.scanLimited(),
                result.errors(),
                result.warnings()
        );
    }

    private PreviewComputation await(
            Future<PreviewComputation> future,
            PreviewRegistry.Lease lease,
            PreviewDeadline deadline
    ) {
        Duration remaining = remaining(deadline);
        try {
            return future.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw lease.isCanceled() ? canceled() : timeout();
        } catch (CancellationException error) {
            throw lease.isCanceled() ? canceled() : timeout();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw lease.isCanceled() ? canceled() : timeout();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            if (cause instanceof FlinkPreviewTimeoutException) {
                throw timeout();
            }
            throw executionFailure(cause);
        }
    }

    private PreviewResponse response(
            UUID pipelineId,
            PreviewRequest request,
            LogicalPlan plan,
            ExecutionResult result,
            long elapsedMs
    ) {
        int returnedRows = Math.min(result.rows().size(), PipelineExecutionLimits.OUTPUT_ROW_LIMIT);
        boolean truncated = result.rows().size() > PipelineExecutionLimits.OUTPUT_ROW_LIMIT;
        String truncationReason = truncated
                ? "OUTPUT_ROW_LIMIT"
                : result.scanLimited() ? "SCAN_ROW_LIMIT" : null;
        List<InputVersion> inputVersions = plan.inputVersions().stream()
                .map(InputVersion::from)
                .toList();
        return new PreviewResponse(
                request.requestId(),
                pipelineId,
                request.draftRevision(),
                request.nodeId(),
                result.columns(),
                result.rows().subList(0, returnedRows),
                returnedRows,
                result.scannedRows(),
                truncated || result.scanLimited(),
                truncationReason,
                elapsedMs,
                inputVersions,
                result.errors(),
                result.warnings()
        );
    }

    private void ensureActive(PreviewRegistry.Lease lease) {
        if (lease.isStopped()) {
            throw canceled();
        }
    }

    private void ensureBudget(PreviewDeadline deadline) {
        if (deadline.expired()) {
            throw timeout();
        }
    }

    private Duration remaining(PreviewDeadline deadline) {
        Duration remaining = deadline.remaining();
        if (remaining.isZero() || remaining.isNegative()) {
            throw timeout();
        }
        return remaining;
    }

    private ApiException executionFailure(Throwable cause) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (cause instanceof PipelineExecutionException failure) {
            put(details, "nodeId", failure.nodeId());
            put(details, "portId", failure.portId());
            put(details, "field", failure.field());
        }
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PREVIEW_EXECUTION_FAILED",
                cause == null || cause.getMessage() == null ? "预览执行失败" : cause.getMessage(),
                details
        );
    }

    private void put(Map<String, Object> details, String key, String value) {
        if (value != null) {
            details.put(key, value);
        }
    }

    private ApiException timeout() {
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PREVIEW_TIMEOUT", "预览执行超时");
    }

    private ApiException canceled() {
        return new ApiException(HttpStatus.CONFLICT, "PREVIEW_CANCELED", "预览已撤销");
    }

    private record ExecutionResult(
            List<PreviewColumn> columns,
            List<Map<String, Object>> rows,
            Long scannedRows,
            boolean scanLimited,
            List<PreviewIssue> errors,
            List<PreviewIssue> warnings
    ) {
    }

    private record PreviewComputation(LogicalPlan plan, ExecutionResult result) {
    }
}
