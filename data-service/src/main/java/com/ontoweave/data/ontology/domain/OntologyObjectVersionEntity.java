package com.ontoweave.data.ontology.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.VersionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ontology_object_version", schema = "data")
public class OntologyObjectVersionEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "binding_id", nullable = false)
    private UUID bindingId;

    @Column(name = "object_type_id", nullable = false)
    private UUID objectTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_dataset_kind", nullable = false)
    private DatasetKind sourceDatasetKind;

    @Column(name = "source_dataset_id", nullable = false)
    private UUID sourceDatasetId;

    @Column(name = "source_dataset_version_id", nullable = false)
    private UUID sourceDatasetVersionId;

    @Column(name = "source_snapshot_id", nullable = false)
    private long sourceSnapshotId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_snapshot", nullable = false)
    private JsonNode mappingSnapshot;

    @Column(name = "mapping_revision", nullable = false)
    private long mappingRevision;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "cleanup_started_at")
    private Instant cleanupStartedAt;

    @Column(name = "cleanup_completed_at")
    private Instant cleanupCompletedAt;

    @Column(name = "cleanup_attempts", nullable = false)
    private int cleanupAttempts;

    protected OntologyObjectVersionEntity() {
    }

    public OntologyObjectVersionEntity(
            UUID id,
            UUID ownerId,
            UUID bindingId,
            UUID objectTypeId,
            DatasetKind sourceDatasetKind,
            UUID sourceDatasetId,
            UUID sourceDatasetVersionId,
            long sourceSnapshotId,
            JsonNode mappingSnapshot,
            long mappingRevision,
            UUID runId
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.bindingId = bindingId;
        this.objectTypeId = objectTypeId;
        this.sourceDatasetKind = sourceDatasetKind;
        this.sourceDatasetId = sourceDatasetId;
        this.sourceDatasetVersionId = sourceDatasetVersionId;
        this.sourceSnapshotId = sourceSnapshotId;
        this.mappingSnapshot = mappingSnapshot;
        this.mappingRevision = mappingRevision;
        this.runId = runId;
        this.status = VersionStatus.BUILDING;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = VersionStatus.BUILDING;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getBindingId() {
        return bindingId;
    }

    public UUID getObjectTypeId() {
        return objectTypeId;
    }

    public DatasetKind getSourceDatasetKind() {
        return sourceDatasetKind;
    }

    public UUID getSourceDatasetId() {
        return sourceDatasetId;
    }

    public UUID getSourceDatasetVersionId() {
        return sourceDatasetVersionId;
    }

    public long getSourceSnapshotId() {
        return sourceSnapshotId;
    }

    public JsonNode getMappingSnapshot() {
        return mappingSnapshot;
    }

    public long getMappingRevision() {
        return mappingRevision;
    }

    public UUID getRunId() {
        return runId;
    }

    public VersionStatus getStatus() {
        return status;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public Instant getCleanupStartedAt() {
        return cleanupStartedAt;
    }

    public Instant getCleanupCompletedAt() {
        return cleanupCompletedAt;
    }

    public int getCleanupAttempts() {
        return cleanupAttempts;
    }

    public void markBuilding() {
        status = VersionStatus.BUILDING;
    }

    public void markActive(Instant now) {
        status = VersionStatus.ACTIVE;
        activatedAt = now;
    }

    public void markRetired(Instant now) {
        status = VersionStatus.RETIRED;
        retiredAt = now;
    }

    public void markFailed() {
        status = VersionStatus.FAILED;
    }

    public void markDiscarded() {
        status = VersionStatus.DISCARDED;
    }

    public void updateCounts(long rowCount, int warningCount) {
        this.rowCount = rowCount;
        this.warningCount = warningCount;
    }
}
