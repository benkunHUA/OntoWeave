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
@Table(name = "pipeline_version", schema = "data")
public class PipelineVersionEntity {

    @Id
    private UUID id;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode definition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode outputs;

    @Column(name = "definition_hash", nullable = false)
    private String definitionHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_refs", nullable = false)
    private JsonNode inputRefs;

    @Column(name = "source_draft_revision", nullable = false)
    private long sourceDraftRevision;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PipelineVersionEntity() {
    }

    public PipelineVersionEntity(
            UUID id,
            UUID pipelineId,
            int versionNumber,
            JsonNode definition,
            JsonNode outputs,
            String definitionHash,
            JsonNode inputRefs,
            long sourceDraftRevision,
            UUID createdBy
    ) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.versionNumber = versionNumber;
        this.definition = definition;
        this.outputs = outputs;
        this.definitionHash = definitionHash;
        this.inputRefs = inputRefs;
        this.sourceDraftRevision = sourceDraftRevision;
        this.createdBy = createdBy;
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

    public UUID getPipelineId() {
        return pipelineId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public JsonNode getDefinition() {
        return definition;
    }

    public JsonNode getOutputs() {
        return outputs;
    }

    public String getDefinitionHash() {
        return definitionHash;
    }

    public JsonNode getInputRefs() {
        return inputRefs;
    }

    public long getSourceDraftRevision() {
        return sourceDraftRevision;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
