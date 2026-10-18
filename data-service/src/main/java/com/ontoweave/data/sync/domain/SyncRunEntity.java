package com.ontoweave.data.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_run", schema = "data")
public class SyncRunEntity {

    @Id
    private UUID id;

    @Column(name = "sync_task_id", nullable = false)
    private UUID syncTaskId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "run_mode", nullable = false)
    private SyncMode runMode;

    @Column(nullable = false)
    private SyncRunStatus status;

    @Column(name = "flink_job_id")
    private String flinkJobId;

    @Column(name = "flink_job_name", nullable = false, unique = true)
    private String flinkJobName;

    @Column(name = "flink_job_state")
    private String flinkJobState;

    @Column(name = "iceberg_catalog", nullable = false)
    private String icebergCatalog;

    @Column(name = "iceberg_namespace", nullable = false)
    private String icebergNamespace;

    @Column(name = "iceberg_table", nullable = false)
    private String icebergTable;

    @Column(name = "server_id_start")
    private Long serverIdStart;

    @Column(name = "server_id_end")
    private Long serverIdEnd;

    @Column(name = "snapshot_expected_rows")
    private Long snapshotExpectedRows;

    @Column(name = "snapshot_completed_at")
    private Instant snapshotCompletedAt;

    @Column(name = "last_checkpoint_at")
    private Instant lastCheckpointAt;

    @Column(name = "last_snapshot_id")
    private Long lastSnapshotId;

    @Column(name = "last_published_snapshot_id")
    private Long lastPublishedSnapshotId;

    @Column(name = "version_interval_seconds", nullable = false)
    private int versionIntervalSeconds;

    @Column(name = "next_version_due_at")
    private Instant nextVersionDueAt;

    @Column(name = "savepoint_path")
    private String savepointPath;

    @Column(name = "stop_requested_at")
    private Instant stopRequestedAt;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "dispatch_attempts", nullable = false)
    private int dispatchAttempts;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncRunEntity() {
    }

    public SyncRunEntity(
            UUID id,
            UUID syncTaskId,
            UUID datasetId,
            UUID operationId,
            SyncMode runMode,
            String flinkJobName,
            String icebergCatalog,
            String icebergNamespace,
            String icebergTable,
            int versionIntervalSeconds
    ) {
        this.id = id;
        this.syncTaskId = syncTaskId;
        this.datasetId = datasetId;
        this.operationId = operationId;
        this.runMode = runMode;
        this.status = SyncRunStatus.QUEUED;
        this.flinkJobName = flinkJobName;
        this.icebergCatalog = icebergCatalog;
        this.icebergNamespace = icebergNamespace;
        this.icebergTable = icebergTable;
        this.versionIntervalSeconds = versionIntervalSeconds;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void assignServerIds(long start, long end) {
        this.serverIdStart = start;
        this.serverIdEnd = end;
    }

    public void recordSnapshotBaseline(long expectedRows) {
        this.snapshotExpectedRows = expectedRows;
    }

    public void requeue() {
        this.status = SyncRunStatus.QUEUED;
        this.dispatchedAt = null;
        this.flinkJobId = null;
        this.flinkJobState = null;
        this.startedAt = null;
    }

    public void markSubmitted(String jobId) {
        this.flinkJobId = jobId;
        this.status = SyncRunStatus.SUBMITTED;
        this.startedAt = Instant.now();
    }

    public void recordFlinkState(String jobState) {
        this.flinkJobState = jobState;
        if ("RUNNING".equalsIgnoreCase(jobState) && status == SyncRunStatus.SUBMITTED) {
            status = SyncRunStatus.RUNNING;
        }
    }

    public void recordCheckpoint(Instant checkpointAt, Long snapshotId) {
        if (checkpointAt != null) {
            this.lastCheckpointAt = checkpointAt;
        }
        if (snapshotId != null) {
            this.lastSnapshotId = snapshotId;
        }
    }

    public void markSnapshotCompleted(Instant completedAt) {
        this.snapshotCompletedAt = completedAt;
        this.nextVersionDueAt = completedAt.plusSeconds(versionIntervalSeconds);
    }

    public void markStopping() {
        if (!status.terminal()) {
            this.status = SyncRunStatus.STOPPING;
        }
    }

    public void markStopRequested(Instant requestedAt) {
        this.stopRequestedAt = requestedAt;
    }

    public void markSucceeded() {
        this.status = SyncRunStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
    }

    public void markStopped(String savepointPath) {
        this.status = SyncRunStatus.STOPPED;
        this.savepointPath = savepointPath;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String code, String message) {
        this.status = SyncRunStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = message;
        this.finishedAt = Instant.now();
    }

    public void recordPublishedSnapshot(long snapshotId) {
        this.lastPublishedSnapshotId = snapshotId;
        this.nextVersionDueAt = Instant.now().plusSeconds(versionIntervalSeconds);
    }

    public void scheduleNextVersion() {
        this.nextVersionDueAt = Instant.now().plusSeconds(versionIntervalSeconds);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSyncTaskId() {
        return syncTaskId;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public SyncMode getRunMode() {
        return runMode;
    }

    public SyncRunStatus getStatus() {
        return status;
    }

    public String getFlinkJobId() {
        return flinkJobId;
    }

    public String getFlinkJobName() {
        return flinkJobName;
    }

    public String getFlinkJobState() {
        return flinkJobState;
    }

    public String getIcebergCatalog() {
        return icebergCatalog;
    }

    public String getIcebergNamespace() {
        return icebergNamespace;
    }

    public String getIcebergTable() {
        return icebergTable;
    }

    public Long getServerIdStart() {
        return serverIdStart;
    }

    public Long getServerIdEnd() {
        return serverIdEnd;
    }

    public Long getSnapshotExpectedRows() {
        return snapshotExpectedRows;
    }

    public Instant getSnapshotCompletedAt() {
        return snapshotCompletedAt;
    }

    public Instant getLastCheckpointAt() {
        return lastCheckpointAt;
    }

    public Long getLastSnapshotId() {
        return lastSnapshotId;
    }

    public Long getLastPublishedSnapshotId() {
        return lastPublishedSnapshotId;
    }

    public int getVersionIntervalSeconds() {
        return versionIntervalSeconds;
    }

    public Instant getNextVersionDueAt() {
        return nextVersionDueAt;
    }

    public String getSavepointPath() {
        return savepointPath;
    }

    public Instant getStopRequestedAt() {
        return stopRequestedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getDispatchAttempts() {
        return dispatchAttempts;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
