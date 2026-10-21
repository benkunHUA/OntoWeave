package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class PreviewRegistry {

    private final Duration releaseGrace;
    private final Duration epochStateTtl;
    private final LongSupplier clock;
    private final Object lock = new Object();
    private final Map<UUID, PreviewLease> activeByPipeline = new HashMap<>();
    private final Map<UUID, Set<PreviewLease>> drainingByPipeline = new HashMap<>();
    private final Map<UUID, Integer> activeByOwner = new HashMap<>();
    private final Map<UUID, PreviewEpochState> epochStates = new HashMap<>();

    PreviewRegistry() {
        this(
                PipelineExecutionLimits.PREVIEW_RELEASE_GRACE,
                PipelineExecutionLimits.PREVIEW_EPOCH_STATE_TTL,
                System::nanoTime
        );
    }

    PreviewRegistry(Duration releaseGrace) {
        this(releaseGrace, PipelineExecutionLimits.PREVIEW_EPOCH_STATE_TTL, System::nanoTime);
    }

    PreviewRegistry(Duration releaseGrace, Duration epochStateTtl, LongSupplier clock) {
        this.releaseGrace = releaseGrace;
        this.epochStateTtl = epochStateTtl;
        this.clock = clock;
    }

    PreviewLease register(UUID pipelineId, UUID ownerId) {
        try (PreviewEpochTicket ticket = beginEpoch(pipelineId)) {
            return register(pipelineId, ownerId, ticket.epoch(), releaseGrace);
        }
    }

    PreviewLease register(UUID pipelineId, UUID ownerId, Duration budget) {
        try (PreviewEpochTicket ticket = beginEpoch(pipelineId)) {
            return register(pipelineId, ownerId, ticket.epoch(), budget);
        }
    }

    PreviewLease register(UUID pipelineId, UUID ownerId, long epoch) {
        return register(pipelineId, ownerId, epoch, releaseGrace);
    }

    PreviewEpochTicket beginEpoch(UUID pipelineId) {
        long now = clock.getAsLong();
        synchronized (lock) {
            cleanup(now);
            PreviewEpochState state = epochStates.computeIfAbsent(
                    pipelineId, ignored -> new PreviewEpochState()
            );
            state.issued++;
            state.pending++;
            state.lastUsed = now;
            long epoch = state.issued;
            return new PreviewEpochTicket(epoch, () -> closeEpoch(pipelineId, state));
        }
    }

    PreviewLease register(UUID pipelineId, UUID ownerId, long epoch, Duration budget) {
        long now = System.nanoTime();
        long graceDeadline = now + releaseGrace.toNanos();
        long budgetDeadline = now + budget.toNanos();
        if (budget.isZero() || budget.isNegative()) {
            throw new BudgetExceededException();
        }
        while (true) {
            PreviewLease existing;
            synchronized (lock) {
                long stateNow = clock.getAsLong();
                cleanup(stateNow);
                PreviewEpochState state = epochStates.get(pipelineId);
                if (state == null || epoch < state.registered || epoch <= state.cutoff) {
                    throw PreviewErrorMapper.canceled();
                }
                existing = activeByPipeline.get(pipelineId);
                if (existing == null) {
                    int ownerActive = activeByOwner.getOrDefault(ownerId, 0);
                    if (ownerActive >= PipelineExecutionLimits.MAX_ACTIVE_PREVIEWS_PER_USER) {
                        throw PreviewErrorMapper.busy();
                    }
                    PreviewLease lease = new PreviewLease();
                    activeByPipeline.put(pipelineId, lease);
                    activeByOwner.put(ownerId, ownerActive + 1);
                    state.registered = Math.max(state.registered, epoch);
                    state.lastUsed = stateNow;
                    return lease;
                }
                existing.markCanceled();
                state.lastUsed = stateNow;
                lock.notifyAll();
            }
            existing.cancelTask();
            awaitRelease(pipelineId, existing, graceDeadline, budgetDeadline);
        }
    }

    void cancelForBuild(UUID pipelineId) {
        PreviewLease lease;
        long now = clock.getAsLong();
        synchronized (lock) {
            cleanup(now);
            PreviewEpochState state = epochStates.get(pipelineId);
            if (state != null) {
                state.cutoff = Math.max(state.cutoff, state.issued);
                state.lastUsed = now;
            }
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

    void taskFinished(UUID pipelineId, UUID ownerId, PreviewLease lease) {
        long now = clock.getAsLong();
        synchronized (lock) {
            lease.markFinished();
            if (activeByPipeline.remove(pipelineId, lease)) {
                decrementOwner(ownerId);
            }
            removeDraining(pipelineId, lease);
            touch(pipelineId, now);
            cleanup(now);
            lock.notifyAll();
        }
    }

    void unregister(UUID pipelineId, UUID ownerId, PreviewLease lease) {
        long now = clock.getAsLong();
        synchronized (lock) {
            if (activeByPipeline.remove(pipelineId, lease)) {
                decrementOwner(ownerId);
                if (lease.hasTask() && !lease.isFinished()) {
                    drainingByPipeline.computeIfAbsent(pipelineId, ignored -> new HashSet<>())
                            .add(lease);
                }
            }
            touch(pipelineId, now);
            cleanup(now);
            lock.notifyAll();
        }
    }

    private void closeEpoch(UUID pipelineId, PreviewEpochState state) {
        long now = clock.getAsLong();
        synchronized (lock) {
            if (epochStates.get(pipelineId) == state) {
                if (state.pending > 0) {
                    state.pending--;
                }
                state.lastUsed = now;
            }
            cleanup(now);
        }
    }

    private void touch(UUID pipelineId, long now) {
        PreviewEpochState state = epochStates.get(pipelineId);
        if (state != null) {
            state.lastUsed = now;
        }
    }

    private void cleanup(long now) {
        epochStates.entrySet().removeIf(entry -> {
            PreviewEpochState state = entry.getValue();
            return state.pending == 0
                    && now - state.lastUsed > epochStateTtl.toNanos()
                    && !hasActiveOrDraining(entry.getKey());
        });
    }

    private void removeDraining(UUID pipelineId, PreviewLease lease) {
        Set<PreviewLease> draining = drainingByPipeline.get(pipelineId);
        if (draining == null) {
            return;
        }
        draining.remove(lease);
        if (draining.isEmpty()) {
            drainingByPipeline.remove(pipelineId);
        }
    }

    private boolean hasActiveOrDraining(UUID pipelineId) {
        Set<PreviewLease> draining = drainingByPipeline.get(pipelineId);
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

    int epochStateCount() {
        synchronized (lock) {
            return epochStates.size();
        }
    }

    boolean hasEpochState(UUID pipelineId) {
        synchronized (lock) {
            return epochStates.containsKey(pipelineId);
        }
    }

    int pendingRequests(UUID pipelineId) {
        synchronized (lock) {
            PreviewEpochState state = epochStates.get(pipelineId);
            return state == null ? 0 : state.pending;
        }
    }

    boolean awaitCancellationRequested(UUID pipelineId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (lock) {
            while (true) {
                PreviewLease lease = activeByPipeline.get(pipelineId);
                if (lease != null && lease.isCanceled()) {
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
            PreviewLease lease,
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

}
