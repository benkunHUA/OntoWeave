package com.ontoweave.data.pipeline.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_schedule_run", schema = "data")
public class PipelineScheduleRunEntity {

    @Id
    private UUID id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(nullable = false)
    private PipelineScheduleRunStatus status;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "build_id", unique = true)
    private UUID buildId;

    @Column(name = "logic_hash")
    private String logicHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_inputs", nullable = false)
    private JsonNode resolvedInputs;

    @Column(name = "reason_code")
    private String reasonCode;

    private String message;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PipelineScheduleRunEntity() {
    }

    public PipelineScheduleRunEntity(
            UUID id,
            UUID scheduleId,
            UUID pipelineId,
            PipelineScheduleRunStatus status,
            String triggerType,
            Instant scheduledFor,
            JsonNode resolvedInputs
    ) {
        this.id = id;
        this.scheduleId = scheduleId;
        this.pipelineId = pipelineId;
        this.status = status;
        this.triggerType = triggerType;
        this.scheduledFor = scheduledFor;
        this.resolvedInputs = resolvedInputs;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public PipelineScheduleRunStatus getStatus() {
        return status;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public UUID getBuildId() {
        return buildId;
    }

    public String getLogicHash() {
        return logicHash;
    }

    public JsonNode getResolvedInputs() {
        return resolvedInputs;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getMessage() {
        return message;
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

    public void evaluate(String logicHash, JsonNode resolvedInputs) {
        this.logicHash = logicHash;
        this.resolvedInputs = resolvedInputs;
    }

    public void markPending(String logicHash, JsonNode resolvedInputs) {
        this.status = PipelineScheduleRunStatus.PENDING;
        evaluate(logicHash, resolvedInputs);
        this.reasonCode = null;
        this.message = null;
        this.buildId = null;
    }

    public void markDispatched(
            UUID buildId,
            String logicHash,
            JsonNode resolvedInputs,
            Instant now
    ) {
        this.status = PipelineScheduleRunStatus.DISPATCHED;
        this.buildId = buildId;
        evaluate(logicHash, resolvedInputs);
        this.reasonCode = null;
        this.message = null;
        this.startedAt = now;
        this.finishedAt = null;
    }

    public void markIgnored(String reasonCode, String message, Instant now) {
        this.status = PipelineScheduleRunStatus.IGNORED;
        this.reasonCode = reasonCode;
        this.message = message;
        this.finishedAt = now;
    }

    public void markFailed(String reasonCode, String message, Instant now) {
        this.status = PipelineScheduleRunStatus.FAILED;
        this.reasonCode = reasonCode;
        this.message = message;
        this.finishedAt = now;
    }
}
