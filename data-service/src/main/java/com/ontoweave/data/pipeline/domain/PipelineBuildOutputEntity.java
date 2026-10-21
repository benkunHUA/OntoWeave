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
@Table(name = "pipeline_build_output", schema = "data")
public class PipelineBuildOutputEntity {

    @Id
    private UUID id;

    @Column(name = "build_id", nullable = false)
    private UUID buildId;

    @Column(name = "output_id", nullable = false)
    private UUID outputId;

    @Column(name = "output_type", nullable = false)
    private PipelineOutputType outputType;

    @Column(nullable = false)
    private PipelineOutputStatus status;

    private PipelineBuildStage stage;

    @Column(name = "engine_job_id")
    private String engineJobId;

    @Column(name = "log_uri")
    private String logUri;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode result;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode stats;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PipelineBuildOutputEntity() {
    }

    public PipelineBuildOutputEntity(
            UUID id,
            UUID buildId,
            UUID outputId,
            PipelineOutputType outputType,
            PipelineOutputStatus status,
            JsonNode result,
            JsonNode stats
    ) {
        this.id = id;
        this.buildId = buildId;
        this.outputId = outputId;
        this.outputType = outputType;
        this.status = status;
        this.result = result;
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

    public UUID getBuildId() {
        return buildId;
    }

    public UUID getOutputId() {
        return outputId;
    }

    public PipelineOutputType getOutputType() {
        return outputType;
    }

    public PipelineOutputStatus getStatus() {
        return status;
    }

    public PipelineBuildStage getStage() {
        return stage;
    }

    public String getEngineJobId() {
        return engineJobId;
    }

    public String getLogUri() {
        return logUri;
    }

    public JsonNode getResult() {
        return result;
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
        this.status = PipelineOutputStatus.RUNNING;
        this.stage = stage;
        this.startedAt = now;
    }

    public void updateStage(PipelineBuildStage stage) {
        this.stage = stage;
    }

    public void recordEngineJob(String engineJobId) {
        recordEngineJob(engineJobId, null);
    }

    public void recordEngineJob(String engineJobId, String logUri) {
        this.engineJobId = engineJobId;
        if (logUri != null && !logUri.isBlank()) {
            this.logUri = logUri;
        }
    }

    public void markSucceeded(PipelineBuildStage stage, JsonNode result, JsonNode stats, Instant now) {
        this.status = PipelineOutputStatus.SUCCEEDED;
        this.stage = stage;
        this.result = result;
        this.stats = stats;
        this.finishedAt = now;
    }

    public void markFailed(
            PipelineBuildStage stage,
            String errorCode,
            String errorMessage,
            JsonNode result,
            JsonNode stats,
            Instant now
    ) {
        this.status = PipelineOutputStatus.FAILED;
        this.stage = stage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.result = result;
        this.stats = stats;
        this.finishedAt = now;
    }

    public void markCanceled(Instant now) {
        this.status = PipelineOutputStatus.CANCELED;
        this.stage = PipelineBuildStage.FINISHED;
        this.finishedAt = now;
    }
}
