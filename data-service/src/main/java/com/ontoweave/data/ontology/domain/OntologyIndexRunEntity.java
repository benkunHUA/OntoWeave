package com.ontoweave.data.ontology.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RunPhase;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
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
@Table(name = "ontology_index_run", schema = "data")
public class OntologyIndexRunEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_kind", nullable = false)
    private SubjectKind subjectKind;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "binding_id")
    private UUID bindingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_dataset_kind", nullable = false)
    private DatasetKind targetDatasetKind;

    @Column(name = "target_dataset_version_id", nullable = false)
    private UUID targetDatasetVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_snapshot", nullable = false)
    private JsonNode mappingSnapshot;

    @Column(name = "mapping_revision", nullable = false)
    private long mappingRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Enumerated(EnumType.STRING)
    @Column
    private RunPhase phase;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "lease_owner")
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "parent_run_id")
    private UUID parentRunId;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details")
    private JsonNode errorDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private JsonNode stats;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OntologyIndexRunEntity() {
    }

    public OntologyIndexRunEntity(
            UUID id,
            UUID ownerId,
            SubjectKind subjectKind,
            UUID subjectId,
            UUID bindingId,
            DatasetKind targetDatasetKind,
            UUID targetDatasetVersionId,
            JsonNode mappingSnapshot,
            long mappingRevision,
            TriggerType triggerType,
            UUID parentRunId
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.subjectKind = subjectKind;
        this.subjectId = subjectId;
        this.bindingId = bindingId;
        this.targetDatasetKind = targetDatasetKind;
        this.targetDatasetVersionId = targetDatasetVersionId;
        this.mappingSnapshot = mappingSnapshot;
        this.mappingRevision = mappingRevision;
        this.triggerType = triggerType;
        this.parentRunId = parentRunId;
        this.status = RunStatus.QUEUED;
        this.attempt = 1;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (status == null) {
            status = RunStatus.QUEUED;
        }
        if (attempt == 0) {
            attempt = 1;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public SubjectKind getSubjectKind() {
        return subjectKind;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getBindingId() {
        return bindingId;
    }

    public DatasetKind getTargetDatasetKind() {
        return targetDatasetKind;
    }

    public UUID getTargetDatasetVersionId() {
        return targetDatasetVersionId;
    }

    public JsonNode getMappingSnapshot() {
        return mappingSnapshot;
    }

    public long getMappingRevision() {
        return mappingRevision;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public RunStatus getStatus() {
        return status;
    }

    public RunPhase getPhase() {
        return phase;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant getCancelRequestedAt() {
        return cancelRequestedAt;
    }

    public int getAttempt() {
        return attempt;
    }

    public UUID getParentRunId() {
        return parentRunId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JsonNode getErrorDetails() {
        return errorDetails;
    }

    public JsonNode getStats() {
        return stats;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
