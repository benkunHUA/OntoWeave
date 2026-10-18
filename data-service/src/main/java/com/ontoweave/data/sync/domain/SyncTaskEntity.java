package com.ontoweave.data.sync.domain;

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
@Table(name = "sync_task", schema = "data")
public class SyncTaskEntity {

    @Id
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "dataset_id", nullable = false, unique = true)
    private UUID datasetId;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "sync_mode", nullable = false)
    private SyncMode syncMode;

    @Column(name = "version_interval_seconds", nullable = false)
    private int versionIntervalSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "primary_key_columns", nullable = false)
    private JsonNode primaryKeyColumns;

    @Column(nullable = false)
    private SyncTaskStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncTaskEntity() {
    }

    public SyncTaskEntity(
            UUID id,
            UUID connectionId,
            UUID datasetId,
            String schemaName,
            String tableName,
            SyncMode syncMode,
            int versionIntervalSeconds,
            JsonNode primaryKeyColumns
    ) {
        this.id = id;
        this.connectionId = connectionId;
        this.datasetId = datasetId;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.syncMode = syncMode;
        this.versionIntervalSeconds = versionIntervalSeconds;
        this.primaryKeyColumns = primaryKeyColumns;
        this.status = SyncTaskStatus.ENABLED;
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

    public void update(SyncMode mode, int intervalSeconds, JsonNode keys) {
        this.syncMode = mode;
        this.versionIntervalSeconds = intervalSeconds;
        this.primaryKeyColumns = keys;
        this.status = SyncTaskStatus.ENABLED;
    }

    public void disable() {
        this.status = SyncTaskStatus.DISABLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public SyncMode getSyncMode() {
        return syncMode;
    }

    public int getVersionIntervalSeconds() {
        return versionIntervalSeconds;
    }

    public JsonNode getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    public SyncTaskStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
