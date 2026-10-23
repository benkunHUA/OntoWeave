package com.ontoweave.data.ontology.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.DatasetKind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OntologyDatasetCatalog {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OntologyDatasetCatalog(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public DatasetPage listBindable(UUID ownerId, String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        String search = keyword == null ? "" : keyword.trim();
        String like = "%" + search + "%";

        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT d.id
                    FROM data.dataset d
                    JOIN data.data_connection c ON c.id = d.connection_id
                    JOIN data.dataset_version v ON v.id = d.current_version_id
                    WHERE c.owner_id = ? AND d.sync_task_id IS NOT NULL
                      AND v.published_at IS NOT NULL
                      AND (? = '' OR d.name ILIKE ?)
                    UNION ALL
                    SELECT d.id
                    FROM data.pipeline_dataset d
                    JOIN data.pipeline p ON p.id = d.pipeline_id
                    JOIN data.pipeline_dataset_version v ON v.id = d.current_version_id
                    WHERE p.owner_id = ? AND d.archived_at IS NULL
                      AND p.archived_at IS NULL
                      AND v.published_at IS NOT NULL
                      AND (? = '' OR d.name ILIKE ?)
                ) catalog
                """, Long.class, ownerId, search, like, ownerId, search, like);

        List<DatasetRef> items = jdbc.query("""
                SELECT * FROM (
                    SELECT 'SYNC' AS dataset_kind, d.id AS dataset_id, d.name,
                           c.owner_id, v.id AS version_id, v.iceberg_catalog,
                           v.iceberg_namespace, v.iceberg_table, v.iceberg_snapshot_id,
                           v.schema_json, v.row_count
                    FROM data.dataset d
                    JOIN data.data_connection c ON c.id = d.connection_id
                    JOIN data.dataset_version v ON v.id = d.current_version_id
                    WHERE c.owner_id = ? AND d.sync_task_id IS NOT NULL
                      AND v.published_at IS NOT NULL
                      AND (? = '' OR d.name ILIKE ?)
                    UNION ALL
                    SELECT 'PIPELINE', d.id, d.name, p.owner_id, v.id,
                           v.iceberg_catalog, v.iceberg_namespace, v.iceberg_table,
                           v.iceberg_snapshot_id, v.schema_json, v.row_count
                    FROM data.pipeline_dataset d
                    JOIN data.pipeline p ON p.id = d.pipeline_id
                    JOIN data.pipeline_dataset_version v ON v.id = d.current_version_id
                    WHERE p.owner_id = ? AND d.archived_at IS NULL
                      AND p.archived_at IS NULL
                      AND v.published_at IS NOT NULL
                      AND (? = '' OR d.name ILIKE ?)
                ) catalog
                ORDER BY name ASC, dataset_id ASC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> mapRef(
                        DatasetKind.from(rs.getString("dataset_kind")),
                        rs
                ), ownerId, search, like, ownerId, search, like,
                safeSize, (safePage - 1) * safeSize);

        return new DatasetPage(items, total == null ? 0 : total, safePage, safeSize);
    }

    public Optional<DatasetRef> resolve(DatasetKind kind, UUID datasetId) {
        if (kind == null || datasetId == null) {
            return Optional.empty();
        }
        return switch (kind) {
            case SYNC -> findSync(datasetId);
            case PIPELINE -> findPipeline(datasetId);
        };
    }

    public DatasetRef requireCurrent(DatasetKind kind, UUID datasetId, UUID ownerId) {
        return resolve(kind, datasetId)
                .filter(ref -> ownerId != null && ownerId.equals(ref.ownerId()))
                .orElseThrow(() -> new DatasetUnavailableException(kind, datasetId));
    }

    public List<DatasetColumn> listColumns(DatasetKind kind, UUID datasetId, UUID ownerId) {
        DatasetRef ref = requireCurrent(kind, datasetId, ownerId);
        return DatasetSchema.columns(ref.schemaJson());
    }

    private Optional<DatasetRef> findSync(UUID datasetId) {
        return jdbc.query("""
                SELECT 'SYNC' AS dataset_kind, d.id AS dataset_id,
                       c.owner_id, v.id AS version_id, v.iceberg_catalog,
                       v.iceberg_namespace, v.iceberg_table, v.iceberg_snapshot_id,
                       v.schema_json, v.row_count
                FROM data.dataset d
                JOIN data.data_connection c ON c.id = d.connection_id
                JOIN data.dataset_version v ON v.id = d.current_version_id
                WHERE d.id = ? AND d.sync_task_id IS NOT NULL
                  AND v.published_at IS NOT NULL
                """, (rs, rowNum) -> mapRef(DatasetKind.SYNC, rs), datasetId)
                .stream()
                .findFirst();
    }

    private Optional<DatasetRef> findPipeline(UUID datasetId) {
        return jdbc.query("""
                SELECT 'PIPELINE' AS dataset_kind, d.id AS dataset_id,
                       p.owner_id, v.id AS version_id, v.iceberg_catalog,
                       v.iceberg_namespace, v.iceberg_table, v.iceberg_snapshot_id,
                       v.schema_json, v.row_count
                FROM data.pipeline_dataset d
                JOIN data.pipeline p ON p.id = d.pipeline_id
                JOIN data.pipeline_dataset_version v ON v.id = d.current_version_id
                WHERE d.id = ? AND d.archived_at IS NULL
                  AND p.archived_at IS NULL
                  AND v.published_at IS NOT NULL
                """, (rs, rowNum) -> mapRef(DatasetKind.PIPELINE, rs), datasetId)
                .stream()
                .findFirst();
    }

    private DatasetRef mapRef(DatasetKind kind, java.sql.ResultSet row) throws java.sql.SQLException {
        return new DatasetRef(
                kind,
                row.getObject("dataset_id", UUID.class),
                row.getObject("version_id", UUID.class),
                row.getString("iceberg_catalog"),
                row.getString("iceberg_namespace"),
                row.getString("iceberg_table"),
                row.getLong("iceberg_snapshot_id"),
                parseSchema(row.getString("schema_json")),
                row.getObject("owner_id", UUID.class),
                row.getLong("row_count")
        );
    }

    private JsonNode parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(schemaJson);
        } catch (Exception exception) {
            throw new IllegalStateException("dataset schema_json is invalid", exception);
        }
    }
}
