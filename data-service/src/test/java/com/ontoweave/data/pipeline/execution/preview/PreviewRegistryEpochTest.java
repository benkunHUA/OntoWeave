package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewRegistryEpochTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final AtomicLong clock = new AtomicLong();
    private final PreviewRegistry registry = new PreviewRegistry(
            Duration.ofSeconds(1), TTL, clock::get
    );

    @Test
    void reclaimsIdleEpochStateAfterTtlUsingTheMonotonicClock() {
        UUID pipelineId = UUID.randomUUID();
        try (PreviewEpochTicket ticket = registry.beginEpoch(pipelineId)) {
            assertThat(ticket.epoch()).isPositive();
        }
        assertThat(registry.hasEpochState(pipelineId)).isTrue();

        clock.set(TTL.toNanos() + 1);
        beginAndClose(UUID.randomUUID());

        assertThat(registry.hasEpochState(pipelineId)).isFalse();
    }

    @Test
    void keepsBuildCutoffForTheTtlWindow() {
        UUID pipelineId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        long oldEpoch;
        try (PreviewEpochTicket ticket = registry.beginEpoch(pipelineId)) {
            oldEpoch = ticket.epoch();
        }
        registry.cancelForBuild(pipelineId);
        clock.set(TTL.toNanos() - 1);

        try (PreviewEpochTicket ticket = registry.beginEpoch(pipelineId)) {
            assertThatThrownBy(() -> registry.register(pipelineId, ownerId, oldEpoch))
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.status().value()).isEqualTo(409);
                        assertThat(exception.code()).isEqualTo("PREVIEW_CANCELED");
                    });
            assertThat(ticket.epoch()).isGreaterThan(oldEpoch);
        }
    }

    @Test
    void doesNotReclaimStateWhilePendingActiveOrDraining() {
        UUID ownerId = UUID.randomUUID();
        UUID pendingPipeline = UUID.randomUUID();
        UUID activePipeline = UUID.randomUUID();
        UUID drainingPipeline = UUID.randomUUID();
        PreviewEpochTicket pending = registry.beginEpoch(pendingPipeline);
        PreviewLease active = registry.register(activePipeline, ownerId);
        PreviewLease draining = registry.register(drainingPipeline, ownerId);
        draining.attach(new FutureTask<>(() -> null));
        registry.unregister(drainingPipeline, ownerId, draining);

        clock.set(TTL.toNanos() + 1);
        beginAndClose(UUID.randomUUID());

        assertThat(registry.hasEpochState(pendingPipeline)).isTrue();
        assertThat(registry.hasEpochState(activePipeline)).isTrue();
        assertThat(registry.hasEpochState(drainingPipeline)).isTrue();

        pending.close();
        registry.unregister(activePipeline, ownerId, active);
        registry.taskFinished(drainingPipeline, ownerId, draining);
        clock.addAndGet(TTL.toNanos() + 1);
        beginAndClose(UUID.randomUUID());

        assertThat(registry.hasEpochState(pendingPipeline)).isFalse();
        assertThat(registry.hasEpochState(activePipeline)).isFalse();
        assertThat(registry.hasEpochState(drainingPipeline)).isFalse();
    }

    private void beginAndClose(UUID pipelineId) {
        try (PreviewEpochTicket ignored = registry.beginEpoch(pipelineId)) {
            assertThat(ignored.epoch()).isPositive();
        }
    }
}
