package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_output", schema = "data")
public class PipelineOutputEntity {

    private static final String ACTIVE_STATUS = "active";
    private static final String ARCHIVED_STATUS = "archived";

    @Id
    private UUID id;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "output_type", nullable = false)
    private PipelineOutputType outputType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PipelineOutputEntity() {
    }

    public PipelineOutputEntity(
            UUID id,
            UUID pipelineId,
            UUID nodeId,
            PipelineOutputType outputType,
            String name
    ) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.nodeId = nodeId;
        this.outputType = outputType;
        this.name = name;
        this.status = ACTIVE_STATUS;
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

    public void rename(String name) {
        this.name = name;
    }

    public void archive() {
        this.status = ARCHIVED_STATUS;
        this.archivedAt = Instant.now();
    }

    public void activate() {
        this.status = ACTIVE_STATUS;
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public PipelineOutputType getOutputType() {
        return outputType;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
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
}
