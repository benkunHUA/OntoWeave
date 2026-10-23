package com.ontoweave.data.ontology.index.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexFailureCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Component
public class RunDatasetResolver {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RunDatasetResolver(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public DatasetRef require(DatasetKind kind, UUID versionId, UUID ownerId) {
        return find(kind, versionId)
                .filter(ref -> ownerId == null || ownerId.equals(ref.ownerId()))
                .orElseThrow(() -> new IndexBuildException(
                        IndexFailureCode.DATASET_NOT_FOUND,
                        "dataset version is unavailable: " + versionId
                ));
    }

    public Optional<DatasetRef> find(DatasetKind kind, UUID versionId) {
        if (kind == null || versionId == null) {
            return Optional.empty();
        }
        return switch (kind) {
            case SYNC -> findSync(versionId);
            case PIPELINE -> findPipeline(versionId);
        };
    }

    private Optional<DatasetRef> findSync(UUID versionId) {
        return jdbc.query("""
                SELECT d.id AS dataset_id, v.id AS version_id, v.iceberg_catalog,
                       v.iceberg_namespace, v.iceberg_table, v.iceberg_snapshot_id,
                       v.schema_json, c.owner_id, v.row_count
                FROM data.dataset_version v
                JOIN data.dataset d ON d.id = v.dataset_id
                JOIN data.data_connection c ON c.id = d.connection_id
                WHERE v.id = ?
                """, (rs, rowNum) -> map(DatasetKind.SYNC, rs), versionId)
                .stream()
                .findFirst();
    }

    private Optional<DatasetRef> findPipeline(UUID versionId) {
        return jdbc.query("""
                SELECT d.id AS dataset_id, v.id AS version_id, v.iceberg_catalog,
                       v.iceberg_namespace, v.iceberg_table, v.iceberg_snapshot_id,
                       v.schema_json, p.owner_id, v.row_count
                FROM data.pipeline_dataset_version v
                JOIN data.pipeline_dataset d ON d.id = v.dataset_id
                JOIN data.pipeline p ON p.id = d.pipeline_id
                WHERE v.id = ?
                """, (rs, rowNum) -> map(DatasetKind.PIPELINE, rs), versionId)
                .stream()
                .findFirst();
    }

    private DatasetRef map(DatasetKind kind, ResultSet row) throws SQLException {
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
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(schemaJson);
        } catch (Exception exception) {
            throw new IndexBuildException(
                    IndexFailureCode.DATASET_NOT_FOUND,
                    "dataset schema_json is invalid",
                    java.util.Map.of(),
                    exception
            );
        }
    }
}
