package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.check.PipelineCheckResult;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.execution.flink.FlinkExecutionResult;
import com.ontoweave.data.pipeline.execution.flink.FlinkPreviewTimeoutException;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final int MAX_ACTIVE_PER_PIPELINE = 1;
    private static final int MAX_ACTIVE_PER_USER = 2;

    private final PipelineCheckService checks;
    private final PreviewPlanBuilder planBuilder;
    private final FlinkTableExecutor executor;
    private final IcebergPreviewReader reader;
    private final Duration timeout;
    private final ExecutorService workers;
    private final boolean ownsWorkers;
    private final Object registryLock = new Object();
    private final Map<UUID, ActivePreview> activeByPipeline = new HashMap<>();
    private final Map<UUID, Integer> activeByOwner = new HashMap<>();

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
                true
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
        this(checks, planBuilder, executor, reader, timeout, workers, false);
    }

    private PipelinePreviewServiceImpl(
            PipelineCheckService checks,
            PreviewPlanBuilder planBuilder,
            FlinkTableExecutor executor,
            IcebergPreviewReader reader,
            Duration timeout,
            ExecutorService workers,
            boolean ownsWorkers
    ) {
        this.checks = checks;
        this.planBuilder = planBuilder;
        this.executor = executor;
        this.reader = reader;
        this.timeout = timeout;
        this.workers = workers;
        this.ownsWorkers = ownsWorkers;
    }

    @PreDestroy
    void shutdownWorkers() {
        if (ownsWorkers) {
            workers.shutdownNow();
        }
    }

    @Override
    public PreviewResponse preview(UUID pipelineId, UUID ownerId, PreviewRequest request) {
        long startedAt = System.nanoTime();
        PipelineCheckResult check = checks.check(pipelineId, ownerId, request.draftRevision());
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

        ActivePreview active = register(pipelineId, ownerId);
        try {
            Future<ExecutionResult> future = workers.submit(() -> execute(plan));
            active.future = future;
            if (active.canceledForBuild) {
                future.cancel(true);
            }
            ExecutionResult result = await(future, active);
            return response(pipelineId, request, plan, result, startedAt);
        } finally {
            unregister(pipelineId, ownerId, active);
        }
    }

    @Override
    public void cancelForBuild(UUID pipelineId) {
        ActivePreview active;
        synchronized (registryLock) {
            active = activeByPipeline.get(pipelineId);
            if (active == null) {
                return;
            }
            active.canceledForBuild = true;
        }
        Future<?> future = active.future;
        if (future != null) {
            future.cancel(true);
        }
    }

    private ExecutionResult execute(LogicalPlan plan) {
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
                    preview.columns().stream()
                            .map(column -> new PreviewColumn(column.name(), column.type(), column.nullable()))
                            .toList(),
                    preview.rows(),
                    null,
                    false,
                    List.of(),
                    List.of()
            );
        }
        FlinkExecutionResult result = executor.execute(
                plan, PipelineExecutionLimits.OUTPUT_ROW_LIMIT, timeout
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

    private ExecutionResult await(Future<ExecutionResult> future, ActivePreview active) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw canceled(active) ? canceled() : timeout();
        } catch (CancellationException error) {
            throw canceled(active) ? canceled() : timeout();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw canceled(active) ? canceled() : timeout();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            if (cause instanceof FlinkPreviewTimeoutException) {
                throw timeout();
            }
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREVIEW_EXECUTION_FAILED",
                    cause == null ? "预览执行失败" : cause.getMessage()
            );
        }
    }

    private PreviewResponse response(
            UUID pipelineId,
            PreviewRequest request,
            LogicalPlan plan,
            ExecutionResult result,
            long startedAt
    ) {
        int returnedRows = Math.min(result.rows().size(), PipelineExecutionLimits.OUTPUT_ROW_LIMIT);
        boolean truncated = result.rows().size() > PipelineExecutionLimits.OUTPUT_ROW_LIMIT;
        String truncationReason = truncated
                ? "OUTPUT_ROW_LIMIT"
                : result.scanLimited() ? "SCAN_ROW_LIMIT" : null;
        List<InputVersion> inputVersions = plan.inputVersions().stream()
                .map(InputVersion::from)
                .toList();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
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

    private ActivePreview register(UUID pipelineId, UUID ownerId) {
        synchronized (registryLock) {
            if (activeByPipeline.containsKey(pipelineId)) {
                throw busy();
            }
            int ownerActive = activeByOwner.getOrDefault(ownerId, 0);
            if (ownerActive >= MAX_ACTIVE_PER_USER) {
                throw busy();
            }
            ActivePreview active = new ActivePreview();
            activeByPipeline.put(pipelineId, active);
            activeByOwner.put(ownerId, ownerActive + 1);
            return active;
        }
    }

    private void unregister(UUID pipelineId, UUID ownerId, ActivePreview active) {
        synchronized (registryLock) {
            activeByPipeline.remove(pipelineId, active);
            activeByOwner.computeIfPresent(ownerId, (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    private boolean canceled(ActivePreview active) {
        return active.canceledForBuild;
    }

    private ApiException busy() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PREVIEW_BUSY", "预览资源繁忙，请稍后重试");
    }

    private ApiException timeout() {
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PREVIEW_TIMEOUT", "预览执行超时");
    }

    private ApiException canceled() {
        return new ApiException(HttpStatus.CONFLICT, "PREVIEW_CANCELED", "预览已因构建优先而取消");
    }

    private static final class ActivePreview {

        private volatile Future<?> future;
        private volatile boolean canceledForBuild;
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
}
