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
@Table(name = "pipeline", schema = "data")
public class PipelineEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private UUID conversationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private PipelineStatus status;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PipelineEntity() {
    }

    public PipelineEntity(UUID id, UUID ownerId, UUID conversationId, String name) {
        this.id = id;
        this.ownerId = ownerId;
        this.conversationId = conversationId;
        this.name = name;
        this.status = PipelineStatus.DRAFT;
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

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void archive() {
        if (status == PipelineStatus.ARCHIVED) {
            return;
        }
        this.status = PipelineStatus.ARCHIVED;
        this.archivedAt = Instant.now();
    }

    public PipelineStatus getStatus() {
        return status;
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
}
