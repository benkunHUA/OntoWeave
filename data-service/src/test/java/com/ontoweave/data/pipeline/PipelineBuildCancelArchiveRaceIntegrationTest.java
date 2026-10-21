package com.ontoweave.data.pipeline;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineBuildCancelArchiveRaceIntegrationTest extends PipelineBuildIntegrationTestSupport {

    @Autowired
    private PipelineBuildService service;

    @Autowired
    private DataSource dataSource;

    @Test
    void cancelRechecksArchiveAfterWaitingForBuildRowLock() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.RUNNING, UUID.randomUUID(),
                Instant.now(), Instant.now(), null
        );
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection locker = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            lockBuild(locker, buildId);
            Future<Throwable> cancelled = pool.submit(() -> {
                try {
                    service.requestCancel(pipelineId, buildId, ownerId);
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });

            awaitCancelWaitingOnBuild();
            jdbc.update(
                    "UPDATE data.pipeline SET status = 'archived', archived_at = now() WHERE id = ?",
                    pipelineId
            );
            locker.rollback();

            Throwable error = cancelled.get(10, TimeUnit.SECONDS);
            assertThat(error).isInstanceOfSatisfying(ApiException.class, api -> {
                assertThat(api.status().value()).isEqualTo(409);
                assertThat(api.code()).isEqualTo("PIPELINE_ARCHIVED");
            });
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, buildId
        )).isEqualTo("running");
        assertThat(jdbc.queryForObject(
                "SELECT cancel_requested_at FROM data.pipeline_build WHERE id = ?",
                java.time.OffsetDateTime.class,
                buildId
        )).isNull();
    }

    private void lockBuild(Connection connection, UUID buildId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM data.pipeline_build WHERE id = ? FOR UPDATE"
        )) {
            statement.setObject(1, buildId);
            statement.executeQuery().close();
        }
    }

    private void awaitCancelWaitingOnBuild() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND wait_event_type = 'Lock'
                      AND query ILIKE '%data.pipeline_build%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("cancel did not wait for the build row lock");
    }
}
