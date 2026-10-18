package com.ontoweave.data.sync.domain;

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
@Table(name = "dataset_version", schema = "data")
public class DatasetVersionEntity {

    @Id
    private UUID id;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

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

    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "row_count_estimated", nullable = false)
    private boolean rowCountEstimated;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false)
    private JsonNode schemaJson;

    @Column(name = "column_count", nullable = false)
    private int columnCount;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "version_source", nullable = false)
    private VersionSource versionSource;

    protected DatasetVersionEntity() {
    }

    public DatasetVersionEntity(
            UUID id,
            UUID datasetId,
            int versionNumber,
            String catalog,
            String namespace,
            String table,
            long snapshotId,
            long rowCount,
            boolean rowCountEstimated,
            long byteSize,
            JsonNode schemaJson,
            int columnCount,
            Instant committedAt,
            VersionSource source
    ) {
        this.id = id;
        this.datasetId = datasetId;
        this.versionNumber = versionNumber;
        this.icebergCatalog = catalog;
        this.icebergNamespace = namespace;
        this.icebergTable = table;
        this.icebergSnapshotId = snapshotId;
        this.rowCount = rowCount;
        this.rowCountEstimated = rowCountEstimated;
        this.byteSize = byteSize;
        this.schemaJson = schemaJson;
        this.columnCount = columnCount;
        this.committedAt = committedAt;
        this.versionSource = source;
    }

    @PrePersist
    void onCreate() {
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getDatasetId() {
        return datasetId;
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

    public long getRowCount() {
        return rowCount;
    }

    public boolean isRowCountEstimated() {
        return rowCountEstimated;
    }

    public long getByteSize() {
        return byteSize;
    }

    public JsonNode getSchemaJson() {
        return schemaJson;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public VersionSource getVersionSource() {
        return versionSource;
    }
}
