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
@Table(name = "pipeline_draft", schema = "data")
public class PipelineDraftEntity {

    @Id
    @Column(name = "pipeline_id")
    private UUID pipelineId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode definition;

    @Column(nullable = false)
    private long revision;

    @Column(name = "updated_by_type", nullable = false)
    private String updatedByType;

    @Column(name = "updated_by_id")
    private UUID updatedById;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PipelineDraftEntity() {
    }

    public PipelineDraftEntity(
            UUID pipelineId,
            JsonNode definition,
            String updatedByType,
            UUID updatedById
    ) {
        this.pipelineId = pipelineId;
        this.definition = definition;
        this.updatedByType = updatedByType;
        this.updatedById = updatedById;
    }

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public JsonNode getDefinition() {
        return definition;
    }

    public long getRevision() {
        return revision;
    }

    public String getUpdatedByType() {
        return updatedByType;
    }

    public UUID getUpdatedById() {
        return updatedById;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
