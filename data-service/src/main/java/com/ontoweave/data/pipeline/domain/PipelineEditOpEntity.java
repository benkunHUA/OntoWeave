package com.ontoweave.data.pipeline.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_edit_op", schema = "data")
public class PipelineEditOpEntity {

    @Id
    private UUID id;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false)
    private int seq;

    @Column(name = "op_type", nullable = false)
    private String opType;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode before;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode after;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "turn_id")
    private UUID turnId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PipelineEditOpEntity() {
    }

    public PipelineEditOpEntity(
            UUID id,
            UUID pipelineId,
            long revision,
            int seq,
            String opType,
            String targetType,
            UUID targetId,
            JsonNode before,
            JsonNode after,
            String actorType,
            UUID actorId,
            UUID operationId,
            UUID turnId
    ) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.revision = revision;
        this.seq = seq;
        this.opType = opType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.before = before;
        this.after = after;
        this.actorType = actorType;
        this.actorId = actorId;
        this.operationId = operationId;
        this.turnId = turnId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public long getRevision() {
        return revision;
    }

    public int getSeq() {
        return seq;
    }

    public String getOpType() {
        return opType;
    }

    public String getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public JsonNode getBefore() {
        return before;
    }

    public JsonNode getAfter() {
        return after;
    }

    public String getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
