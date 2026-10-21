package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.execution.flink.FlinkPreviewTimeoutException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class PreviewExecutionEngine {

    private final PreviewStageExecutor stages;
    private final Duration timeout;
    private final ExecutorService workers;
    private final boolean ownsWorkers;
    private final PreviewRegistry registry;

    PreviewExecutionEngine(
            PreviewStageExecutor stages,
            Duration timeout,
            ExecutorService workers,
            boolean ownsWorkers,
            PreviewRegistry registry
    ) {
        this.stages = stages;
        this.timeout = timeout;
        this.workers = workers;
        this.ownsWorkers = ownsWorkers;
        this.registry = registry;
    }

    void shutdown() {
        if (ownsWorkers) {
            workers.shutdownNow();
        }
    }

    PreviewResponse preview(UUID pipelineId, UUID ownerId, PreviewRequest request) {
        PreviewDeadline deadline = PreviewDeadline.start(timeout);
        stages.assertOwned(pipelineId, ownerId);
        try (PreviewEpochTicket ticket = registry.beginEpoch(pipelineId)) {
            return previewOwned(pipelineId, ownerId, request, deadline, ticket.epoch());
        }
    }

    private PreviewResponse previewOwned(
            UUID pipelineId,
            UUID ownerId,
            PreviewRequest request,
            PreviewDeadline deadline,
            long epoch
    ) {
        validateRevision(pipelineId, ownerId, request.draftRevision(), deadline);
        ensureBudget(deadline);
        PreviewLease lease;
        try {
            lease = registry.register(pipelineId, ownerId, epoch, deadline.remaining());
        } catch (PreviewRegistry.BudgetExceededException error) {
            throw PreviewErrorMapper.timeout();
        }
        Future<PreviewStageExecutor.PreviewComputation> future;
        try {
            ensureActive(lease);
            ensureBudget(deadline);
            future = submitTracked(pipelineId, ownerId, lease, () -> compute(
                    pipelineId, ownerId, request, lease, deadline
            ));
            lease.attach(future);
            PreviewStageExecutor.PreviewComputation computation = await(future, lease, deadline);
            ensureActive(lease);
            ensureBudget(deadline);
            return PreviewResponseMapper.response(
                    pipelineId, request, computation.plan(), computation.result(), deadline.elapsedMs()
            );
        } catch (ApiException error) {
            if (lease.isCanceled()) {
                throw PreviewErrorMapper.canceled();
            }
            throw error;
        } catch (RuntimeException error) {
            if (lease.isCanceled()) {
                throw PreviewErrorMapper.canceled();
            }
            throw PreviewErrorMapper.executionFailure(error);
        } finally {
            lease.detach();
            registry.unregister(pipelineId, ownerId, lease);
        }
    }

    private void validateRevision(
            UUID pipelineId,
            UUID ownerId,
            long revision,
            PreviewDeadline deadline
    ) {
        Duration remaining = remaining(deadline);
        Future<?> future = submit(() -> {
            stages.validateRevision(pipelineId, ownerId, revision);
            return null;
        });
        try {
            future.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw PreviewErrorMapper.timeout();
        } catch (CancellationException error) {
            throw PreviewErrorMapper.timeout();
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw PreviewErrorMapper.timeout();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            throw PreviewErrorMapper.executionFailure(cause);
        }
    }

    private PreviewStageExecutor.PreviewComputation compute(
            UUID pipelineId,
            UUID ownerId,
            PreviewRequest request,
            PreviewLease lease,
            PreviewDeadline deadline
    ) {
        return stages.compute(pipelineId, ownerId, request, lease, deadline);
    }

    private PreviewStageExecutor.PreviewComputation await(
            Future<PreviewStageExecutor.PreviewComputation> future,
            PreviewLease lease,
            PreviewDeadline deadline
    ) {
        Duration remaining = remaining(deadline);
        try {
            return future.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw lease.isCanceled() ? PreviewErrorMapper.canceled() : PreviewErrorMapper.timeout();
        } catch (CancellationException error) {
            throw lease.isCanceled() ? PreviewErrorMapper.canceled() : PreviewErrorMapper.timeout();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw lease.isCanceled() ? PreviewErrorMapper.canceled() : PreviewErrorMapper.timeout();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ApiException apiException) {
                throw apiException;
            }
            if (cause instanceof CancellationException) {
                throw lease.isCanceled() ? PreviewErrorMapper.canceled() : PreviewErrorMapper.timeout();
            }
            if (cause instanceof FlinkPreviewTimeoutException) {
                throw PreviewErrorMapper.timeout();
            }
            throw PreviewErrorMapper.executionFailure(cause);
        }
    }

    private <T> Future<T> submit(Callable<T> task) {
        try {
            return workers.submit(task);
        } catch (RejectedExecutionException error) {
            throw PreviewErrorMapper.busy();
        }
    }

    private Future<PreviewStageExecutor.PreviewComputation> submitTracked(
            UUID pipelineId,
            UUID ownerId,
            PreviewLease lease,
            Callable<PreviewStageExecutor.PreviewComputation> computation
    ) {
        AtomicBoolean started = new AtomicBoolean();
        FutureTask<PreviewStageExecutor.PreviewComputation> task =
                new FutureTask<>(() -> {
                    started.set(true);
                    try {
                        return computation.call();
                    } finally {
                        registry.taskFinished(pipelineId, ownerId, lease);
                    }
                }) {
                    @Override
                    protected void done() {
                        if (!started.get()) {
                            registry.taskFinished(pipelineId, ownerId, lease);
                        }
                    }
                };
        try {
            workers.execute(task);
        } catch (RejectedExecutionException error) {
            throw PreviewErrorMapper.busy();
        }
        return task;
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
}
