package com.ontoweave.data.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dataset", schema = "data")
public class DatasetEntity {

    @Id
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "sync_task_id")
    private UUID syncTaskId;

    @Column(nullable = false)
    private String name;

    @Column(name = "active_iceberg_catalog")
    private String activeIcebergCatalog;

    @Column(name = "active_iceberg_namespace")
    private String activeIcebergNamespace;

    @Column(name = "active_iceberg_table")
    private String activeIcebergTable;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DatasetEntity() {
    }

    public DatasetEntity(UUID id, UUID connectionId, String name) {
        this.id = id;
        this.connectionId = connectionId;
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void bindTask(UUID taskId) {
        this.syncTaskId = taskId;
    }

    public void activateVersion(
            UUID versionId,
            String catalog,
            String namespace,
            String table
    ) {
        this.currentVersionId = versionId;
        this.activeIcebergCatalog = catalog;
        this.activeIcebergNamespace = namespace;
        this.activeIcebergTable = table;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public UUID getSyncTaskId() {
        return syncTaskId;
    }

    public String getName() {
        return name;
    }

    public String getActiveIcebergCatalog() {
        return activeIcebergCatalog;
    }

    public String getActiveIcebergNamespace() {
        return activeIcebergNamespace;
    }

    public String getActiveIcebergTable() {
        return activeIcebergTable;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
