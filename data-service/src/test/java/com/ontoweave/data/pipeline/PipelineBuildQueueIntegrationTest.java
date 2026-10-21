package com.ontoweave.data.pipeline;

import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildQueue;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildExecutor;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildService;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PipelineBuildQueueIntegrationTest extends PipelineBuildIntegrationTestSupport {

    @Autowired
    private PipelineBuildQueue queue;

    @Autowired
    private PipelineBuildService service;

    @Autowired
    private PipelineBuildExecutor executor;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private PipelineDatasetWriter writer;

    @MockitoBean
    private FlinkRestClient flink;

    @Test
    void claimNextSkipsAQueuedBuildLockedByAnotherTransaction() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID lockedPipeline = insertPipeline(ownerId);
        UUID lockedVersion = insertVersion(
                lockedPipeline, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        UUID claimablePipeline = insertPipeline(ownerId);
        UUID claimableVersion = insertVersion(
                claimablePipeline, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        UUID locked = insertBuild(
                lockedPipeline, lockedVersion, PipelineBuildStatus.QUEUED, UUID.randomUUID()
        );
        UUID claimable = insertBuild(
                claimablePipeline, claimableVersion, PipelineBuildStatus.QUEUED, UUID.randomUUID()
        );
        jdbc.update(
                "UPDATE data.pipeline_build SET created_at = now() - interval '2 minutes' WHERE id = ?",
                locked
        );
        jdbc.update(
                "UPDATE data.pipeline_build SET created_at = now() - interval '1 minute' WHERE id = ?",
                claimable
        );

        CountDownLatch lockedByOther = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> holderError = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    jdbc.queryForObject(
                            "SELECT id FROM data.pipeline_build WHERE id = ? FOR UPDATE",
                            UUID.class,
                            locked
                    );
                    lockedByOther.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Throwable error) {
                holderError.set(error);
            }
        });
        holder.start();
        assertThat(lockedByOther.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(queue.claimNext()).isEqualTo(claimable);
        } finally {
            release.countDown();
            holder.join(10_000);
        }

        assertThat(holderError.get()).isNull();
        assertThat(status(locked)).isEqualTo("queued");
        assertThat(status(claimable)).isEqualTo("running");
        assertThat(jdbc.queryForObject(
                "SELECT started_at IS NOT NULL AND heartbeat_at IS NOT NULL "
                        + "FROM data.pipeline_build WHERE id = ?",
                Boolean.class,
                claimable
        )).isTrue();
    }

    @Test
    void recoverLostMarksStaleBuildsFailedWithoutPublishing() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        OutputFixture output = insertOutput(pipelineId, "orders");
        Instant stale = Instant.now().minus(Duration.ofHours(1));
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.RUNNING, UUID.randomUUID(),
                stale, stale, null
        );
        insertBuildOutput(
                buildId, output.outputId(), PipelineOutputStatus.RUNNING, mapper.createObjectNode()
        );

        int recovered = queue.recoverLost(Duration.ofMinutes(1));

        assertThat(recovered).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, buildId
        )).isEqualTo("failed");
        assertThat(jdbc.queryForObject(
                "SELECT error_code FROM data.pipeline_build WHERE id = ?", String.class, buildId
        )).isEqualTo("BUILD_WORKER_LOST");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build_output WHERE build_id = ?", String.class, buildId
        )).isEqualTo("failed");
        assertUnpublished(pipelineId, output.datasetId());
        verify(writer, never()).cancel(buildId);
    }

    @Test
    void recoverLostMarksCancellationAsCanceledOnlyAfterTheTerminalStateCommits() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        OutputFixture output = insertOutput(pipelineId, "orders");
        Instant stale = Instant.now().minus(Duration.ofHours(1));
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.RUNNING, UUID.randomUUID(),
                stale, stale, Instant.now()
        );
        insertBuildOutput(
                buildId, output.outputId(), PipelineOutputStatus.RUNNING, mapper.createObjectNode()
        );
        jdbc.update(
                "UPDATE data.pipeline_build_output SET engine_job_id = 'job-1' WHERE build_id = ?",
                buildId
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(status(buildId)).isEqualTo("canceled");
            return null;
        }).when(flink).cancel("job-1");

        assertThat(queue.recoverLost(Duration.ofMinutes(1))).isEqualTo(1);

        assertThat(status(buildId)).isEqualTo("canceled");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build_output WHERE build_id = ?", String.class, buildId
        )).isEqualTo("canceled");
        assertUnpublished(pipelineId, output.datasetId());
    }

    @Test
    void recoverLostNeverOverwritesASuccessfulBuild() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        Instant stale = Instant.now().minus(Duration.ofHours(1));
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.SUCCEEDED, UUID.randomUUID(),
                stale, stale, null
        );

        assertThat(queue.recoverLost(Duration.ofMinutes(1))).isZero();

        assertThat(status(buildId)).isEqualTo("succeeded");
        verify(writer, never()).cancel(buildId);
    }

    @Test
    void concurrentRecoveryInstancesClaimAStaleBuildOnlyOnce() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        Instant stale = Instant.now().minus(Duration.ofHours(1));
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.RUNNING, UUID.randomUUID(),
                stale, stale, null
        );
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> recoverAfter(start));
            Future<Integer> second = pool.submit(() -> recoverAfter(start));
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(status(buildId)).isEqualTo("failed");
        assertThat(jdbc.queryForObject(
                "SELECT error_code FROM data.pipeline_build WHERE id = ?", String.class, buildId
        )).isEqualTo("BUILD_WORKER_LOST");
    }

    @Test
    void recoverLostSkipsRowsLockedByAnotherRecoveryOrPublisher() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        Instant stale = Instant.now().minus(Duration.ofHours(1));
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.RUNNING, UUID.randomUUID(),
                stale, stale, null
        );
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> holderError = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    jdbc.queryForObject(
                            "SELECT id FROM data.pipeline_build WHERE id = ? FOR UPDATE",
                            UUID.class,
                            buildId
                    );
                    locked.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Throwable error) {
                holderError.set(error);
            }
        });
        holder.start();
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(queue.recoverLost(Duration.ofMinutes(1))).isZero();
            assertThat(status(buildId)).isEqualTo("running");
        } finally {
            release.countDown();
            holder.join(10_000);
        }

        assertThat(holderError.get()).isNull();
        assertThat(queue.recoverLost(Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(status(buildId)).isEqualTo("failed");
    }

    @Test
    void runningCancellationEndsCanceledWithoutPublishing() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        OutputFixture output = insertOutput(pipelineId, "orders");
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.RUNNING, UUID.randomUUID(),
                Instant.now(), Instant.now(), null
        );
        insertBuildOutput(
                buildId, output.outputId(), PipelineOutputStatus.RUNNING, mapper.createObjectNode()
        );

        service.requestCancel(pipelineId, buildId, ownerId);
        assertThat(queue.heartbeat(buildId)).isTrue();
        executor.execute(buildId);

        assertThat(status(buildId)).isEqualTo("canceled");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build_output WHERE build_id = ?", String.class, buildId
        )).isEqualTo("canceled");
        assertUnpublished(pipelineId, output.datasetId());
        verify(writer, times(1)).cancel(buildId);
    }

    @Test
    void heartbeatRefreshesTimestampAndReportsCancellationWithoutCallingFlink() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        Instant stale = Instant.now().minus(Duration.ofHours(1));
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.RUNNING, UUID.randomUUID(),
                stale, stale, Instant.now()
        );
        assertThat(queue.heartbeat(buildId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT heartbeat_at > now() - interval '1 minute' FROM data.pipeline_build WHERE id = ?",
                Boolean.class,
                buildId
        )).isTrue();

        assertThat(queue.heartbeat(buildId)).isTrue();

        verify(writer, never()).cancel(buildId);
    }

    @Test
    void cancelOnlyRequestsCancellationAndTheWorkerNeverPublishes() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        OutputFixture output = insertOutput(pipelineId, "orders");
        UUID buildId = insertBuild(
                pipelineId, versionId, PipelineBuildStatus.QUEUED, UUID.randomUUID()
        );
        insertBuildOutput(
                buildId, output.outputId(), PipelineOutputStatus.QUEUED, mapper.createObjectNode()
        );

        service.requestCancel(pipelineId, buildId, ownerId);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, buildId
        )).isEqualTo("queued");
        assertThat(jdbc.queryForObject(
                "SELECT cancel_requested_at IS NOT NULL FROM data.pipeline_build WHERE id = ?",
                Boolean.class,
                buildId
        )).isTrue();
        assertUnpublished(pipelineId, output.datasetId());

        assertThat(queue.claimNext()).isNull();

        assertThat(status(buildId)).isEqualTo("canceled");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build_output WHERE build_id = ?", String.class, buildId
        )).isEqualTo("canceled");
        assertUnpublished(pipelineId, output.datasetId());
    }

    private String status(UUID buildId) {
        return jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, buildId
        );
    }

    private int recoverAfter(CountDownLatch start) throws InterruptedException {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("recovery barrier timed out");
        }
        return queue.recoverLost(Duration.ofMinutes(1));
    }

    private void assertUnpublished(UUID pipelineId, UUID datasetId) {
        assertThat(jdbc.queryForObject(
                "SELECT current_version_id FROM data.pipeline WHERE id = ?",
                UUID.class,
                pipelineId
        )).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT current_version_id FROM data.pipeline_dataset WHERE id = ?",
                UUID.class,
                datasetId
        )).isNull();
        assertThat(count("data.pipeline_dataset_version")).isZero();
        assertThat(count("data.outbox_event")).isZero();
    }
}
