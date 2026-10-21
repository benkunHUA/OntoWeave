package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class PreviewRegistry {

    private final Duration releaseGrace;
    private final Object lock = new Object();
    private final Map<UUID, Lease> activeByPipeline = new HashMap<>();
    private final Map<UUID, Integer> activeByOwner = new HashMap<>();

    PreviewRegistry() {
        this(PipelineExecutionLimits.PREVIEW_RELEASE_GRACE);
    }

    PreviewRegistry(Duration releaseGrace) {
        this.releaseGrace = releaseGrace;
    }

    Lease register(UUID pipelineId, UUID ownerId) {
        long deadline = System.nanoTime() + releaseGrace.toNanos();
        while (true) {
            Lease existing;
            synchronized (lock) {
                existing = activeByPipeline.get(pipelineId);
                if (existing == null) {
                    int ownerActive = activeByOwner.getOrDefault(ownerId, 0);
                    if (ownerActive >= PipelineExecutionLimits.MAX_ACTIVE_PREVIEWS_PER_USER) {
                        throw busy();
                    }
                    Lease lease = new Lease();
                    activeByPipeline.put(pipelineId, lease);
                    activeByOwner.put(ownerId, ownerActive + 1);
                    return lease;
                }
                existing.markCanceled();
                lock.notifyAll();
            }
            existing.cancelTask();
            awaitRelease(pipelineId, existing, deadline);
        }
    }

    void cancelForBuild(UUID pipelineId) {
        Lease lease;
        synchronized (lock) {
            lease = activeByPipeline.get(pipelineId);
            if (lease == null) {
                return;
            }
            lease.markCanceled();
            lock.notifyAll();
        }
        lease.cancelTask();
    }

    void unregister(UUID pipelineId, UUID ownerId, Lease lease) {
        synchronized (lock) {
            activeByPipeline.remove(pipelineId, lease);
            activeByOwner.computeIfPresent(ownerId, (key, count) -> count <= 1 ? null : count - 1);
            lock.notifyAll();
        }
    }

    boolean awaitCancellationRequested(UUID pipelineId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (lock) {
            while (true) {
                Lease lease = activeByPipeline.get(pipelineId);
                if (lease != null && lease.canceled) {
                    return true;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(lock, remaining);
            }
        }
    }

    private void awaitRelease(UUID pipelineId, Lease lease, long deadline) {
        synchronized (lock) {
            while (activeByPipeline.get(pipelineId) == lease) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw busy();
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw busy();
                }
            }
        }
    }

    private ApiException busy() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PREVIEW_BUSY", "预览资源繁忙，请稍后重试");
    }

    static final class Lease {

        private volatile Future<?> future;
        private volatile boolean canceled;
        private boolean started;
        private boolean finished;

        boolean isCanceled() {
            return canceled;
        }

        void setFuture(Future<?> future) {
            boolean cancel;
            synchronized (this) {
                this.future = future;
                cancel = canceled;
            }
            if (cancel) {
                future.cancel(true);
            }
        }

        void cancelTask() {
            Future<?> current;
            synchronized (this) {
                current = future;
            }
            if (current != null) {
                current.cancel(true);
            }
        }

        void markCanceled() {
            synchronized (this) {
                canceled = true;
                notifyAll();
            }
        }

        boolean beginTask() {
            synchronized (this) {
                if (canceled) {
                    return false;
                }
                started = true;
                return true;
            }
        }

        void finishTask() {
            synchronized (this) {
                finished = true;
                notifyAll();
            }
        }

        void awaitTaskFinished() {
            boolean interrupted = false;
            synchronized (this) {
                while (started && !finished) {
                    try {
                        wait();
                    } catch (InterruptedException error) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
