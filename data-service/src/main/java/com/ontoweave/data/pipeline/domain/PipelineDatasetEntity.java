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
@Table(name = "pipeline_dataset", schema = "data")
public class PipelineDatasetEntity {

    @Id
    private UUID id;

    @Column(name = "output_id", nullable = false, unique = true)
    private UUID outputId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(nullable = false)
    private String name;

    @Column(name = "write_mode", nullable = false)
    private PipelineDatasetWriteMode writeMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "primary_key_columns", nullable = false)
    private JsonNode primaryKeyColumns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode expectations;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PipelineDatasetEntity() {
    }

    public PipelineDatasetEntity(
            UUID id,
            UUID outputId,
            UUID pipelineId,
            String name,
            JsonNode primaryKeyColumns,
            JsonNode expectations
    ) {
        this.id = id;
        this.outputId = outputId;
        this.pipelineId = pipelineId;
        this.name = name;
        this.writeMode = PipelineDatasetWriteMode.SNAPSHOT;
        this.primaryKeyColumns = primaryKeyColumns;
        this.expectations = expectations;
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

    public void updateConfiguration(
            String name,
            PipelineDatasetWriteMode writeMode,
            JsonNode primaryKeyColumns,
            JsonNode expectations
    ) {
        this.name = name;
        this.writeMode = writeMode;
        this.primaryKeyColumns = primaryKeyColumns;
        this.expectations = expectations;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOutputId() {
        return outputId;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public String getName() {
        return name;
    }

    public PipelineDatasetWriteMode getWriteMode() {
        return writeMode;
    }

    public JsonNode getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    public JsonNode getExpectations() {
        return expectations;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void activateVersion(UUID versionId) {
        this.currentVersionId = versionId;
    }
}
