package com.ontoweave.data.sync.execution;

import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.domain.VersionSource;
import com.ontoweave.data.sync.flink.FlinkCheckpointInfo;
import com.ontoweave.data.sync.flink.FlinkJobInfo;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import com.ontoweave.data.sync.version.DatasetVersionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class SyncRunSupervisor {

    private static final Logger log = LoggerFactory.getLogger(SyncRunSupervisor.class);
    private static final List<SyncRunStatus> ACTIVE_JOB_STATUSES = List.of(
            SyncRunStatus.SUBMITTED,
            SyncRunStatus.RUNNING
    );
    private final SyncProperties properties;
    private final SyncRunRepository runs;
    private final FlinkRestClient flink;
    private final IcebergCatalogClient iceberg;
    private final DatasetVersionPublisher publisher;
    private final SyncTaskRepository tasks;
    private final SyncRunEventWriter events;
    private final SyncRunStopInitiator stopInitiator;

    public SyncRunSupervisor(
            SyncProperties properties,
            SyncRunRepository runs,
            FlinkRestClient flink,
            IcebergCatalogClient iceberg,
            DatasetVersionPublisher publisher,
            SyncTaskRepository tasks,
            SyncRunEventWriter events,
            SyncRunStopInitiator stopInitiator
    ) {
        this.properties = properties;
        this.runs = runs;
        this.flink = flink;
        this.iceberg = iceberg;
        this.publisher = publisher;
        this.tasks = tasks;
        this.events = events;
        this.stopInitiator = stopInitiator;
    }

    @Scheduled(fixedDelayString = "${ontoweave.sync.supervisor-interval-ms:5000}")
    public void reconcile() {
        if (!properties.supervisorEnabled()) {
            return;
        }
        for (SyncRunEntity run : runs.findTop50ByStatusInOrderByCreatedAtAsc(SyncRunStatus.FLINK_ACTIVE)) {
            try {
                reconcileRun(run);
            } catch (Exception exception) {
                log.warn("failed to reconcile sync run {}", run.getId(), exception);
            }
        }
    }

    private void reconcileRun(SyncRunEntity run) {
        if (run.getStatus() == SyncRunStatus.PREPARING) {
            recoverPreparing(run);
            return;
        }
        if (run.getStatus() == SyncRunStatus.STOPPING) {
            reconcileStopping(run);
            return;
        }
        if (run.getFlinkJobId() == null) {
            return;
        }
        Optional<FlinkJobInfo> job = flink.job(run.getFlinkJobId());
        if (job.isEmpty()) {
            if (run.getStartedAt() != null && run.getStartedAt().isBefore(Instant.now().minusSeconds(30))) {
                failRun(
                        run,
                        "FLINK_JOB_LOST",
                        "Flink 作业已丢失（job " + run.getFlinkJobId() + "）",
                        null,
                        null
                );
            }
            return;
        }
        FlinkJobInfo info = job.get();
        SyncRunStatus previous = run.getStatus();
        run.recordFlinkState(info.state());
        if (previous == SyncRunStatus.SUBMITTED && run.getStatus() == SyncRunStatus.RUNNING) {
            int updated = runs.markRunning(
                    run.getId(),
                    SyncRunStatus.SUBMITTED,
                    SyncRunStatus.RUNNING,
                    info.state(),
                    Instant.now()
            );
            if (updated != 1) {
                return;
            }
            task(run).ifPresent(value -> events.write(run, value, "sync.run.running", "Flink 作业已开始运行"));
        }
        if ("FINISHED".equalsIgnoreCase(info.state())) {
            if (run.getRunMode() == SyncMode.SNAPSHOT) {
                refreshCheckpoint(run);
                int updated = runs.markSucceeded(
                        run.getId(),
                        ACTIVE_JOB_STATUSES,
                        SyncRunStatus.SUCCEEDED,
                        info.state(),
                        run.getLastCheckpointAt(),
                        run.getLastSnapshotId(),
                        Instant.now()
                );
                if (updated == 1) {
                    run.markSucceeded();
                    task(run).ifPresent(value -> events.write(run, value, "sync.run.succeeded", "快照同步完成"));
                    publisher.publish(run.getId(), VersionSource.SNAPSHOT_END);
                }
            } else {
                failRun(
                        run,
                        "FLINK_JOB_ENDED_UNEXPECTEDLY",
                        "CDC 作业意外结束（job " + run.getFlinkJobId() + "）",
                        info.state(),
                        "CDC 作业意外结束"
                );
            }
            return;
        }
        if ("FAILED".equalsIgnoreCase(info.state())) {
            String detail = flink.failureSummary(run.getFlinkJobId())
                    .orElse("Flink 作业失败：未能从 Flink 读取异常详情（job "
                            + run.getFlinkJobId() + "）");
            failRun(run, "FLINK_JOB_FAILED", detail, info.state(), "Flink 作业失败");
            return;
        }
        if ("CANCELED".equalsIgnoreCase(info.state())) {
            finishStopped(run, null);
            return;
        }
        refreshCheckpoint(run);
        maybeMarkSnapshotCompleted(run);
        int updated = runs.recordProgress(
                run.getId(),
                ACTIVE_JOB_STATUSES,
                info.state(),
                run.getLastCheckpointAt(),
                run.getLastSnapshotId(),
                run.getSnapshotCompletedAt(),
                run.getNextVersionDueAt(),
                Instant.now()
        );
        if (updated != 1) {
            return;
        }
        if (run.getRunMode() == SyncMode.CDC
                && run.getSnapshotCompletedAt() != null
                && run.getLastPublishedSnapshotId() == null) {
            publisher.publish(run.getId(), VersionSource.SNAPSHOT_END);
            return;
        }
        maybePublishScheduled(run);
    }

    private void recoverPreparing(SyncRunEntity run) {
        if (run.getUpdatedAt() != null && run.getUpdatedAt().isAfter(Instant.now().minusSeconds(600))) {
            return;
        }
        if (run.getDispatchAttempts() >= properties.maxDispatchAttempts()) {
            failRun(run, "DISPATCH_EXHAUSTED", "同步命令未能完成投递", null, "同步命令未能完成投递");
        } else {
            runs.requeuePreparing(
                    run.getId(),
                    SyncRunStatus.PREPARING,
                    SyncRunStatus.QUEUED,
                    Instant.now()
            );
        }
    }

    private void reconcileStopping(SyncRunEntity run) {
        if (run.getFlinkJobId() == null) {
            finishStopped(run, null);
            return;
        }
        Optional<FlinkJobInfo> job = flink.job(run.getFlinkJobId());
        if (job.isEmpty()) {
            finishStopped(run, null);
            return;
        }
        String state = job.get().state();
        run.recordFlinkState(state);
        if ("CANCELED".equalsIgnoreCase(state)) {
            finishStopped(run, null);
            return;
        }
        if ("FINISHED".equalsIgnoreCase(state)) {
            finishStopped(run, null);
            return;
        }
        if ("FAILED".equalsIgnoreCase(state)) {
            String detail = flink.failureSummary(run.getFlinkJobId())
                    .orElse("停止过程中作业失败（job " + run.getFlinkJobId() + "）");
            failRun(run, "FLINK_JOB_FAILED", detail, state, "停止过程中作业失败");
            return;
        }
        if (run.getStopRequestedAt() != null
                && run.getStopRequestedAt().isBefore(
                        Instant.now().minusSeconds(properties.stopSavepointTimeoutSeconds())
                )) {
            Instant now = Instant.now();
            if (runs.claimCancelFallback(
                    run.getId(),
                    SyncRunStatus.STOPPING,
                    run.getStopRequestedAt(),
                    now
            ) == 1) {
                run.markStopRequested(now);
                log.info("savepoint stop timed out, canceling Flink job {} for run {}",
                        run.getFlinkJobId(), run.getId());
                flink.cancel(run.getFlinkJobId());
            }
        }
        SyncRunEntity current = run;
        if (current.getStopRequestedAt() == null) {
            stopInitiator.initiate(current);
            current = runs.findById(current.getId()).orElse(current);
            current.recordFlinkState(state);
        }
        runs.recordProgress(
                current.getId(),
                List.of(SyncRunStatus.STOPPING),
                current.getFlinkJobState(),
                current.getLastCheckpointAt(),
                current.getLastSnapshotId(),
                current.getSnapshotCompletedAt(),
                current.getNextVersionDueAt(),
                Instant.now()
        );
    }

    private void failRun(
            SyncRunEntity run,
            String errorCode,
            String errorMessage,
            String flinkJobState,
            String eventSummary
    ) {
        int updated = runs.markFailed(
                run.getId(),
                run.getStatus(),
                SyncRunStatus.FAILED,
                flinkJobState,
                errorCode,
                errorMessage,
                Instant.now()
        );
        if (updated != 1) {
            return;
        }
        run.markFailed(errorCode, errorMessage);
        if (eventSummary != null) {
            task(run).ifPresent(value -> events.write(run, value, "sync.run.failed", eventSummary));
        }
    }

    private void finishStopped(SyncRunEntity run, String savepointPath) {
        refreshCheckpoint(run);
        int updated = runs.markStopped(
                run.getId(),
                SyncRunStatus.STOPPING,
                SyncRunStatus.STOPPED,
                run.getFlinkJobId(),
                run.getFlinkJobState(),
                savepointPath,
                run.getLastCheckpointAt(),
                run.getLastSnapshotId(),
                Instant.now()
        );
        if (updated == 1) {
            run.markStopped(savepointPath);
            task(run).ifPresent(value -> events.write(run, value, "sync.run.stopped", "同步已停止"));
            if (run.getRunMode() == SyncMode.CDC && run.getLastCheckpointAt() != null) {
                publisher.publish(run.getId(), VersionSource.STOP);
            }
        }
    }

    private Optional<com.ontoweave.data.sync.domain.SyncTaskEntity> task(SyncRunEntity run) {
        return tasks.findById(run.getSyncTaskId());
    }

    private void refreshCheckpoint(SyncRunEntity run) {
        if (run.getFlinkJobId() == null) {
            return;
        }
        flink.latestCompletedCheckpoint(run.getFlinkJobId())
                .ifPresent(checkpoint -> run.recordCheckpoint(checkpoint.completedAt(), null));
    }

    private void maybeMarkSnapshotCompleted(SyncRunEntity run) {
        if (run.getSnapshotCompletedAt() != null || run.getSnapshotExpectedRows() == null) {
            return;
        }
        Optional<IcebergSnapshotInfo> latest = Optional.empty();
        if (run.getSnapshotExpectedRows() > 0) {
            latest = iceberg.latestSnapshot(
                    run.getIcebergCatalog(),
                    run.getIcebergNamespace(),
                    run.getIcebergTable()
            );
        }
        if (SnapshotCompletionDecider.completed(run, latest)) {
            run.markSnapshotCompleted(Instant.now());
            latest.ifPresent(snapshot -> run.recordCheckpoint(null, snapshot.snapshotId()));
        }
    }

    private void maybePublishScheduled(SyncRunEntity run) {
        if (run.getRunMode() != SyncMode.CDC
                || run.getSnapshotCompletedAt() == null
                || run.getNextVersionDueAt() == null
                || run.getNextVersionDueAt().isAfter(Instant.now())
                || run.getLastCheckpointAt() == null
                || !run.getLastCheckpointAt().isAfter(run.getSnapshotCompletedAt())) {
            return;
        }
        if (publisher.publish(run.getId(), VersionSource.SCHEDULED).isEmpty()) {
            int updated = runs.scheduleNextVersion(
                    run.getId(),
                    SyncRunStatus.RUNNING,
                    Instant.now().plusSeconds(run.getVersionIntervalSeconds()),
                    Instant.now()
            );
            if (updated == 1) {
                run.scheduleNextVersion();
            }
        }
    }
}
