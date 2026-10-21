package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.common.web.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewRegistryTest {

    private final ExecutorService callers = Executors.newCachedThreadPool();

    @AfterEach
    void shutdownCallers() {
        callers.shutdownNow();
    }

    @Test
    void replacementCancelsOldPreviewAndWaitsForRelease() throws Exception {
        UUID pipelineId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PreviewRegistry registry = new PreviewRegistry(Duration.ofSeconds(1));
        PreviewRegistry.Lease old = registry.register(pipelineId, ownerId);

        Future<PreviewRegistry.Lease> replacement = callers.submit(
                () -> registry.register(pipelineId, ownerId)
        );
        assertThat(registry.awaitCancellationRequested(pipelineId, Duration.ofSeconds(1))).isTrue();
        assertThat(old.isCanceled()).isTrue();
        assertThat(replacement).isNotDone();

        registry.unregister(pipelineId, ownerId, old);
        PreviewRegistry.Lease current = replacement.get(1, TimeUnit.SECONDS);
        registry.unregister(pipelineId, ownerId, current);
    }

    @Test
    void returnsBusyWhenOldPreviewDoesNotReleaseWithinGracePeriod() throws Exception {
        UUID pipelineId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PreviewRegistry registry = new PreviewRegistry(Duration.ofMillis(50));
        PreviewRegistry.Lease old = registry.register(pipelineId, ownerId);
        try {
            Future<PreviewRegistry.Lease> replacement = callers.submit(
                    () -> registry.register(pipelineId, ownerId)
            );
            assertThat(registry.awaitCancellationRequested(pipelineId, Duration.ofSeconds(1))).isTrue();

            assertThatThrownBy(() -> replacement.get(1, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(ApiException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        assertThat(exception.code()).isEqualTo("PREVIEW_BUSY");
                    });
        } finally {
            registry.unregister(pipelineId, ownerId, old);
        }
    }

    @Test
    void returnsBusyAtUserLevelLimitForDifferentPipelines() {
        UUID ownerId = UUID.randomUUID();
        PreviewRegistry registry = new PreviewRegistry(Duration.ofSeconds(1));
        registry.register(UUID.randomUUID(), ownerId);
        registry.register(UUID.randomUUID(), ownerId);

        assertThatThrownBy(() -> registry.register(UUID.randomUUID(), ownerId))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.code()).isEqualTo("PREVIEW_BUSY");
                });
    }
}
