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
@Table(name = "pipeline_dataset_version", schema = "data")
public class PipelineDatasetVersionEntity {

    @Id
    private UUID id;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "build_output_id", nullable = false, unique = true)
    private UUID buildOutputId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "iceberg_catalog", nullable = false)
    private String icebergCatalog;

    @Column(name = "iceberg_namespace", nullable = false)
    private String icebergNamespace;

    @Column(name = "iceberg_table", nullable = false)
    private String icebergTable;

    @Column(name = "iceberg_snapshot_id", nullable = false)
    private long icebergSnapshotId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false)
    private JsonNode schemaJson;

    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PipelineDatasetVersionEntity() {
    }

    public PipelineDatasetVersionEntity(
            UUID id,
            UUID datasetId,
            UUID buildOutputId,
            int versionNumber,
            String icebergCatalog,
            String icebergNamespace,
            String icebergTable,
            long icebergSnapshotId,
            JsonNode schemaJson,
            long rowCount,
            long byteSize,
            Instant committedAt
    ) {
        this.id = id;
        this.datasetId = datasetId;
        this.buildOutputId = buildOutputId;
        this.versionNumber = versionNumber;
        this.icebergCatalog = icebergCatalog;
        this.icebergNamespace = icebergNamespace;
        this.icebergTable = icebergTable;
        this.icebergSnapshotId = icebergSnapshotId;
        this.schemaJson = schemaJson;
        this.rowCount = rowCount;
        this.byteSize = byteSize;
        this.committedAt = committedAt;
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

    public UUID getDatasetId() {
        return datasetId;
    }

    public UUID getBuildOutputId() {
        return buildOutputId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getIcebergCatalog() {
        return icebergCatalog;
    }

    public String getIcebergNamespace() {
        return icebergNamespace;
    }

    public String getIcebergTable() {
        return icebergTable;
    }

    public long getIcebergSnapshotId() {
        return icebergSnapshotId;
    }

    public JsonNode getSchemaJson() {
        return schemaJson;
    }

    public long getRowCount() {
        return rowCount;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
