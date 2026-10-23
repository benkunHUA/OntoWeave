package com.ontoweave.data.ontology.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RelationKind;
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
@Table(name = "ontology_relation_version", schema = "data")
public class OntologyRelationVersionEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "binding_id")
    private UUID bindingId;

    @Column(name = "relation_id", nullable = false)
    private UUID relationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_kind", nullable = false)
    private RelationKind relationKind;

    @Column(name = "from_object_type_id", nullable = false)
    private UUID fromObjectTypeId;

    @Column(name = "to_object_type_id", nullable = false)
    private UUID toObjectTypeId;

    @Column(name = "from_object_version_id", nullable = false)
    private UUID fromObjectVersionId;

    @Column(name = "to_object_version_id", nullable = false)
    private UUID toObjectVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_dataset_kind")
    private DatasetKind sourceDatasetKind;

    @Column(name = "source_dataset_id")
    private UUID sourceDatasetId;

    @Column(name = "source_dataset_version_id")
    private UUID sourceDatasetVersionId;

    @Column(name = "source_snapshot_id")
    private Long sourceSnapshotId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_snapshot")
    private JsonNode mappingSnapshot;

    @Column(name = "mapping_revision")
    private Long mappingRevision;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "skipped_count", nullable = false)
    private long skippedCount;

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

    protected OntologyRelationVersionEntity() {
    }

    public OntologyRelationVersionEntity(
            UUID id,
            UUID ownerId,
            UUID bindingId,
            UUID relationId,
            RelationKind relationKind,
            UUID fromObjectTypeId,
            UUID toObjectTypeId,
            UUID fromObjectVersionId,
            UUID toObjectVersionId,
            DatasetKind sourceDatasetKind,
            UUID sourceDatasetId,
            UUID sourceDatasetVersionId,
            Long sourceSnapshotId,
            JsonNode mappingSnapshot,
            Long mappingRevision,
            UUID runId
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.bindingId = bindingId;
        this.relationId = relationId;
        this.relationKind = relationKind;
        this.fromObjectTypeId = fromObjectTypeId;
        this.toObjectTypeId = toObjectTypeId;
        this.fromObjectVersionId = fromObjectVersionId;
        this.toObjectVersionId = toObjectVersionId;
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

    public UUID getRelationId() {
        return relationId;
    }

    public RelationKind getRelationKind() {
        return relationKind;
    }

    public UUID getFromObjectTypeId() {
        return fromObjectTypeId;
    }

    public UUID getToObjectTypeId() {
        return toObjectTypeId;
    }

    public UUID getFromObjectVersionId() {
        return fromObjectVersionId;
    }

    public UUID getToObjectVersionId() {
        return toObjectVersionId;
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

    public Long getSourceSnapshotId() {
        return sourceSnapshotId;
    }

    public JsonNode getMappingSnapshot() {
        return mappingSnapshot;
    }

    public Long getMappingRevision() {
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

    public long getSkippedCount() {
        return skippedCount;
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

    public void updateCounts(long rowCount, long skippedCount) {
        this.rowCount = rowCount;
        this.skippedCount = skippedCount;
    }
}
