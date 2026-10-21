package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pipeline_schedule", schema = "data")
public class PipelineScheduleEntity {

    private static final String TIME_TRIGGER = "time";

    @Id
    private UUID id;

    @Column(name = "pipeline_id", nullable = false, unique = true)
    private UUID pipelineId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private PipelineScheduleStatus status;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "paused_by")
    private UUID pausedBy;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PipelineScheduleEntity() {
    }

    public PipelineScheduleEntity(
            UUID id,
            UUID pipelineId,
            String name,
            String cronExpression,
            String timezone,
            Instant nextRunAt,
            UUID createdBy
    ) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.name = name;
        this.status = PipelineScheduleStatus.ACTIVE;
        this.triggerType = TIME_TRIGGER;
        this.cronExpression = cronExpression;
        this.timezone = timezone;
        this.nextRunAt = Objects.requireNonNull(nextRunAt, "nextRunAt");
        this.createdBy = createdBy;
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

    public String getName() {
        return name;
    }

    public PipelineScheduleStatus getStatus() {
        return status;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public UUID getPausedBy() {
        return pausedBy;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDefinition(
            String name,
            String cronExpression,
            String timezone,
            Instant nextRunAt
    ) {
        this.name = name;
        this.cronExpression = cronExpression;
        this.timezone = timezone;
        this.nextRunAt = nextRunAt;
        this.status = PipelineScheduleStatus.ACTIVE;
        this.pausedAt = null;
        this.pausedBy = null;
    }

    public void pause(UUID actorId, Instant now) {
        this.status = PipelineScheduleStatus.PAUSED;
        this.nextRunAt = null;
        this.pausedAt = now;
        this.pausedBy = actorId;
    }

    public void resume(Instant nextRunAt, Instant now) {
        this.status = PipelineScheduleStatus.ACTIVE;
        this.nextRunAt = nextRunAt;
        this.pausedAt = null;
        this.pausedBy = null;
        this.updatedAt = now;
    }

    public void advance(Instant nextRunAt, Instant dueAt) {
        this.nextRunAt = nextRunAt;
        this.lastRunAt = dueAt;
    }

    public void recordRun(Instant dueAt) {
        this.lastRunAt = dueAt;
    }
}
