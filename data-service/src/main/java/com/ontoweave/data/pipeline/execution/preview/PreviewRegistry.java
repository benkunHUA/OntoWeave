package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class PreviewRegistry {

    private final Duration releaseGrace;
    private final Object lock = new Object();
    private final Map<UUID, Lease> activeByPipeline = new HashMap<>();
    private final Map<UUID, Set<Lease>> drainingByPipeline = new HashMap<>();
    private final Map<UUID, Integer> activeByOwner = new HashMap<>();
    private final Map<UUID, Long> lastIssuedEpoch = new HashMap<>();
    private final Map<UUID, Long> lastRegisteredEpoch = new HashMap<>();
    private final Map<UUID, Long> buildCutoffEpoch = new HashMap<>();

    PreviewRegistry() {
        this(PipelineExecutionLimits.PREVIEW_RELEASE_GRACE);
    }

    PreviewRegistry(Duration releaseGrace) {
        this.releaseGrace = releaseGrace;
    }

    Lease register(UUID pipelineId, UUID ownerId) {
        return register(pipelineId, ownerId, beginEpoch(pipelineId), releaseGrace);
    }

    Lease register(UUID pipelineId, UUID ownerId, Duration budget) {
        return register(pipelineId, ownerId, beginEpoch(pipelineId), budget);
    }

    Lease register(UUID pipelineId, UUID ownerId, long epoch) {
        return register(pipelineId, ownerId, epoch, releaseGrace);
    }

    long beginEpoch(UUID pipelineId) {
        synchronized (lock) {
            return lastIssuedEpoch.merge(pipelineId, 1L, Long::sum);
        }
    }

    Lease register(UUID pipelineId, UUID ownerId, long epoch, Duration budget) {
        long now = System.nanoTime();
        long graceDeadline = now + releaseGrace.toNanos();
        long budgetDeadline = now + budget.toNanos();
        if (budget.isZero() || budget.isNegative()) {
            throw new BudgetExceededException();
        }
        while (true) {
            Lease existing;
            synchronized (lock) {
                long registered = lastRegisteredEpoch.getOrDefault(pipelineId, 0L);
                if (epoch < registered || epoch <= buildCutoffEpoch.getOrDefault(pipelineId, 0L)) {
                    throw PreviewErrorMapper.canceled();
                }
                existing = activeByPipeline.get(pipelineId);
                if (existing == null) {
                    int ownerActive = activeByOwner.getOrDefault(ownerId, 0);
                    if (ownerActive >= PipelineExecutionLimits.MAX_ACTIVE_PREVIEWS_PER_USER) {
                        throw PreviewErrorMapper.busy();
                    }
                    Lease lease = new Lease();
                    activeByPipeline.put(pipelineId, lease);
                    activeByOwner.put(ownerId, ownerActive + 1);
                    lastRegisteredEpoch.put(pipelineId, epoch);
                    return lease;
                }
                existing.markCanceled();
                lock.notifyAll();
            }
            existing.cancelTask();
            awaitRelease(pipelineId, existing, graceDeadline, budgetDeadline);
        }
    }

    void cancelForBuild(UUID pipelineId) {
        Lease lease;
        synchronized (lock) {
            long cutoff = lastIssuedEpoch.getOrDefault(pipelineId, 0L);
            buildCutoffEpoch.merge(pipelineId, cutoff, Math::max);
            lease = activeByPipeline.get(pipelineId);
            if (lease != null) {
                lease.markCanceled();
            }
            lock.notifyAll();
        }
        if (lease != null) {
            lease.cancelTask();
        }
        awaitReleased(pipelineId, releaseGrace);
    }

    boolean awaitReleased(UUID pipelineId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (lock) {
            while (hasActiveOrDraining(pipelineId)) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    void taskFinished(UUID pipelineId, UUID ownerId, Lease lease) {
        synchronized (lock) {
            lease.markFinished();
            if (activeByPipeline.remove(pipelineId, lease)) {
                decrementOwner(ownerId);
            }
            removeDraining(pipelineId, lease);
            lock.notifyAll();
        }
    }

    void unregister(UUID pipelineId, UUID ownerId, Lease lease) {
        synchronized (lock) {
            if (activeByPipeline.remove(pipelineId, lease)) {
                decrementOwner(ownerId);
                if (lease.hasTask() && !lease.isFinished()) {
                    drainingByPipeline.computeIfAbsent(pipelineId, ignored -> new HashSet<>())
                            .add(lease);
                }
            }
            lock.notifyAll();
        }
    }

    private void removeDraining(UUID pipelineId, Lease lease) {
        Set<Lease> draining = drainingByPipeline.get(pipelineId);
        if (draining == null) {
            return;
        }
        draining.remove(lease);
        if (draining.isEmpty()) {
            drainingByPipeline.remove(pipelineId);
        }
    }

    private boolean hasActiveOrDraining(UUID pipelineId) {
        Set<Lease> draining = drainingByPipeline.get(pipelineId);
        return activeByPipeline.containsKey(pipelineId) || (draining != null && !draining.isEmpty());
    }

    private void decrementOwner(UUID ownerId) {
        activeByOwner.computeIfPresent(ownerId, (key, count) -> count <= 1 ? null : count - 1);
    }

    boolean hasActiveLease(UUID pipelineId) {
        synchronized (lock) {
            return activeByPipeline.containsKey(pipelineId);
        }
    }

    int activeLeases(UUID ownerId) {
        synchronized (lock) {
            return activeByOwner.getOrDefault(ownerId, 0);
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

    private void awaitRelease(
            UUID pipelineId,
            Lease lease,
            long graceDeadline,
            long budgetDeadline
    ) {
        synchronized (lock) {
            while (activeByPipeline.get(pipelineId) == lease) {
                long now = System.nanoTime();
                if (now >= budgetDeadline) {
                    throw new BudgetExceededException();
                }
                long remaining = graceDeadline - now;
                if (remaining <= 0) {
                    throw PreviewErrorMapper.busy();
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(
                            lock, Math.min(remaining, budgetDeadline - now)
                    );
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw PreviewErrorMapper.busy();
                }
            }
        }
    }

    static final class BudgetExceededException extends RuntimeException {
    }

    static final class Lease {

        private volatile Future<?> future;
        private volatile boolean canceled;
        private volatile boolean detached;
        private volatile boolean finished;

        boolean isCanceled() {
            return canceled;
        }

        boolean isStopped() {
            return canceled || detached;
        }

        boolean isFinished() {
            return finished;
        }

        boolean hasTask() {
            return future != null;
        }

        void attach(Future<?> future) {
            boolean cancel;
            synchronized (this) {
                this.future = future;
                cancel = isStopped();
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
            }
        }

        void markFinished() {
            finished = true;
        }

        void detach() {
            Future<?> current;
            synchronized (this) {
                detached = true;
                current = future;
            }
            if (current != null) {
                current.cancel(true);
            }
        }
    }
}
