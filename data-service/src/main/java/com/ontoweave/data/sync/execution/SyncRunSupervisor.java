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
    private static final List<SyncRunStatus> ACTIVE = List.of(
            SyncRunStatus.PREPARING,
            SyncRunStatus.SUBMITTED,
            SyncRunStatus.RUNNING,
            SyncRunStatus.STOPPING
    );

    private final SyncProperties properties;
    private final SyncRunRepository runs;
    private final FlinkRestClient flink;
    private final IcebergCatalogClient iceberg;
    private final DatasetVersionPublisher publisher;
    private final SyncTaskRepository tasks;
    private final SyncRunEventWriter events;

    public SyncRunSupervisor(
            SyncProperties properties,
            SyncRunRepository runs,
            FlinkRestClient flink,
            IcebergCatalogClient iceberg,
            DatasetVersionPublisher publisher,
            SyncTaskRepository tasks,
            SyncRunEventWriter events
    ) {
        this.properties = properties;
        this.runs = runs;
        this.flink = flink;
        this.iceberg = iceberg;
        this.publisher = publisher;
        this.tasks = tasks;
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${ontoweave.sync.supervisor-interval-ms:5000}")
    public void reconcile() {
        if (!properties.supervisorEnabled()) {
            return;
        }
        for (SyncRunEntity run : runs.findTop50ByStatusInOrderByCreatedAtAsc(ACTIVE)) {
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
                run.markFailed("FLINK_JOB_LOST", "Flink 作业已丢失");
                runs.save(run);
            }
            return;
        }
        FlinkJobInfo info = job.get();
        SyncRunStatus previous = run.getStatus();
        run.recordFlinkState(info.state());
        if (previous == SyncRunStatus.SUBMITTED && run.getStatus() == SyncRunStatus.RUNNING) {
            task(run).ifPresent(value -> events.write(run, value, "sync.run.running", "Flink 作业已开始运行"));
        }
        if ("FINISHED".equalsIgnoreCase(info.state())) {
            if (run.getRunMode() == SyncMode.SNAPSHOT) {
                refreshCheckpoint(run);
                run.markSucceeded();
                runs.save(run);
                task(run).ifPresent(value -> events.write(run, value, "sync.run.succeeded", "快照同步完成"));
                publisher.publish(run.getId(), VersionSource.SNAPSHOT_END);
            } else {
                run.markFailed("FLINK_JOB_ENDED_UNEXPECTEDLY", "CDC 作业意外结束");
                runs.save(run);
                task(run).ifPresent(value -> events.write(
                        run, value, "sync.run.failed", "CDC 作业意外结束"
                ));
            }
            return;
        }
        if ("FAILED".equalsIgnoreCase(info.state())) {
            run.markFailed("FLINK_JOB_FAILED", "Flink 作业失败");
            runs.save(run);
            task(run).ifPresent(value -> events.write(run, value, "sync.run.failed", "Flink 作业失败"));
            return;
        }
        if ("CANCELED".equalsIgnoreCase(info.state())) {
            finishStopped(run, null);
            return;
        }
        refreshCheckpoint(run);
        maybeMarkSnapshotCompleted(run);
        runs.save(run);
        maybePublishScheduled(run);
    }

    private void recoverPreparing(SyncRunEntity run) {
        if (run.getUpdatedAt() != null && run.getUpdatedAt().isAfter(Instant.now().minusSeconds(600))) {
            return;
        }
        if (run.getDispatchAttempts() >= properties.maxDispatchAttempts()) {
            run.markFailed("DISPATCH_EXHAUSTED", "同步命令未能完成投递");
            task(run).ifPresent(value -> events.write(
                    run, value, "sync.run.failed", "同步命令未能完成投递"
            ));
        } else {
            run.requeue();
        }
        runs.save(run);
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
            run.markFailed("FLINK_JOB_FAILED", "停止过程中作业失败");
            runs.save(run);
            task(run).ifPresent(value -> events.write(run, value, "sync.run.failed", "停止过程中作业失败"));
            return;
        }
        if (run.getStopRequestedAt() == null) {
            String target = properties.savepointDir() + "/" + run.getId();
            if (run.getRunMode() == SyncMode.CDC) {
                if (flink.stopWithSavepoint(run.getFlinkJobId(), target).isEmpty()) {
                    flink.cancel(run.getFlinkJobId());
                }
            } else {
                flink.cancel(run.getFlinkJobId());
            }
            run.markStopRequested(Instant.now());
        }
        runs.save(run);
    }

    private void finishStopped(SyncRunEntity run, String savepointPath) {
        refreshCheckpoint(run);
        run.markStopped(savepointPath);
        runs.save(run);
        task(run).ifPresent(value -> events.write(run, value, "sync.run.stopped", "同步已停止"));
        if (run.getRunMode() == SyncMode.CDC && run.getLastCheckpointAt() != null) {
            publisher.publish(run.getId(), VersionSource.STOP);
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
            run.scheduleNextVersion();
            runs.save(run);
        }
    }
}
