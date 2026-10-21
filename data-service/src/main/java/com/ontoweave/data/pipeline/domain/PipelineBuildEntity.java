package com.ontoweave.data.pipeline.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_build", schema = "data")
public class PipelineBuildEntity {

    @Id
    private UUID id;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "pipeline_version_id", nullable = false)
    private UUID pipelineVersionId;

    @Column(nullable = false)
    private PipelineBuildStatus status;

    private PipelineBuildStage stage;

    @Column(name = "trigger_type", nullable = false)
    private PipelineBuildTrigger triggerType;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_inputs", nullable = false)
    private JsonNode resolvedInputs;

    @Column(name = "logic_hash")
    private String logicHash;

    @Column(name = "log_uri")
    private String logUri;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode stats;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PipelineBuildEntity() {
    }

    public PipelineBuildEntity(
            UUID id,
            UUID pipelineId,
            UUID pipelineVersionId,
            PipelineBuildStatus status,
            PipelineBuildTrigger triggerType,
            UUID operationId,
            UUID requestedBy,
            JsonNode resolvedInputs,
            JsonNode stats
    ) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.pipelineVersionId = pipelineVersionId;
        this.status = status;
        this.triggerType = triggerType;
        this.operationId = operationId;
        this.requestedBy = requestedBy;
        this.resolvedInputs = resolvedInputs;
        this.stats = stats;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public UUID getPipelineVersionId() {
        return pipelineVersionId;
    }

    public PipelineBuildStatus getStatus() {
        return status;
    }

    public PipelineBuildStage getStage() {
        return stage;
    }

    public PipelineBuildTrigger getTriggerType() {
        return triggerType;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public JsonNode getResolvedInputs() {
        return resolvedInputs;
    }

    public String getLogicHash() {
        return logicHash;
    }

    public String getLogUri() {
        return logUri;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JsonNode getStats() {
        return stats;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public Instant getCancelRequestedAt() {
        return cancelRequestedAt;
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

    public void markRunning(PipelineBuildStage stage, Instant now) {
        this.status = PipelineBuildStatus.RUNNING;
        this.stage = stage;
        this.startedAt = now;
        this.heartbeatAt = now;
    }

    public void heartbeat(Instant now) {
        this.heartbeatAt = now;
    }

    public void requestCancel(Instant now) {
        if (cancelRequestedAt == null) {
            cancelRequestedAt = now;
        }
    }

    public void recordLogUri(String logUri) {
        this.logUri = logUri;
    }

    public void markSucceeded(Instant now) {
        this.status = PipelineBuildStatus.SUCCEEDED;
        this.stage = PipelineBuildStage.FINISHED;
        this.finishedAt = now;
        this.heartbeatAt = now;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage, Instant now) {
        this.status = PipelineBuildStatus.FAILED;
        this.stage = PipelineBuildStage.FINISHED;
        this.finishedAt = now;
        this.heartbeatAt = now;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public void markCanceled(Instant now) {
        this.status = PipelineBuildStatus.CANCELED;
        this.stage = PipelineBuildStage.FINISHED;
        this.finishedAt = now;
        this.heartbeatAt = now;
    }

    public void markQueued() {
        this.status = PipelineBuildStatus.QUEUED;
        this.stage = null;
        this.startedAt = null;
        this.heartbeatAt = null;
        this.finishedAt = null;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void updateLogicHash(String logicHash) {
        this.logicHash = logicHash;
    }
}
