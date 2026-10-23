package com.ontoweave.data.ontology.index.store;

import com.ontoweave.data.ontology.index.OntologyIndexProperties;
import com.ontoweave.data.ontology.store.RelationStoreWriter;
import com.ontoweave.data.ontology.store.StageValidation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class JdbcRelationStoreWriter implements RelationStoreWriter {

    private static final int[] TYPES = {
            Types.OTHER, Types.OTHER, Types.OTHER, Types.VARCHAR, Types.OTHER, Types.VARCHAR
    };

    private final JdbcTemplate jdbc;
    private final int batchSize;

    public JdbcRelationStoreWriter(JdbcTemplate jdbc, OntologyIndexProperties properties) {
        this.jdbc = jdbc;
        this.batchSize = properties.batchSize();
    }

    @Override
    public void write(UUID runId, List<RelationStageRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (RelationStageRow row : rows) {
            batch.add(new Object[]{
                    runId,
                    row.relationId(),
                    row.fromObjectTypeId(),
                    row.fromPrimaryKey(),
                    row.toObjectTypeId(),
                    row.toPrimaryKey()
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO data.ontology_relation_stage
                    (run_id, relation_id, from_object_type_id, from_primary_key,
                     to_object_type_id, to_primary_key)
                VALUES (?, ?, ?, ?, ?, ?)
                """, batch, TYPES);
    }

    @Override
    public StageValidation validate(UUID runId) {
        Long rowCount = jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_relation_stage WHERE run_id = ?",
                Long.class,
                runId
        );
        Long missing = jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_relation_stage
                WHERE run_id = ? AND (
                    from_primary_key IS NULL OR btrim(from_primary_key) = '' OR
                    to_primary_key IS NULL OR btrim(to_primary_key) = ''
                )
                """, Long.class, runId);
        Long duplicates = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT relation_id, from_primary_key, to_primary_key
                    FROM data.ontology_relation_stage WHERE run_id = ?
                    GROUP BY relation_id, from_primary_key, to_primary_key
                    HAVING count(*) > 1
                ) duplicate_pairs
                """, Long.class, runId);
        return new StageValidation(
                (missing == null || missing == 0) && (duplicates == null || duplicates == 0),
                rowCount == null ? 0 : rowCount,
                duplicates == null ? 0 : duplicates,
                missing == null ? 0 : missing,
                List.of()
        );
    }

    @Override
    public void promote(UUID runId, UUID versionId) {
        int promoted;
        do {
            promoted = jdbc.update("""
                    WITH batch AS (
                        DELETE FROM data.ontology_relation_stage
                        WHERE ctid IN (
                            SELECT ctid FROM data.ontology_relation_stage
                            WHERE run_id = ? ORDER BY ctid LIMIT ?
                        )
                        RETURNING relation_id, from_object_type_id, from_primary_key,
                                  to_object_type_id, to_primary_key
                    )
                    INSERT INTO data.ontology_relation_instance
                        (version_id, relation_id, from_object_type_id, from_primary_key,
                         to_object_type_id, to_primary_key)
                    SELECT ?, relation_id, from_object_type_id, from_primary_key,
                           to_object_type_id, to_primary_key
                    FROM batch
                    WHERE from_primary_key IS NOT NULL AND to_primary_key IS NOT NULL
                    ON CONFLICT DO NOTHING
                    """, runId, batchSize, versionId);
        } while (promoted == batchSize);
    }

    @Override
    public void deleteStage(UUID runId) {
        jdbc.update("DELETE FROM data.ontology_relation_stage WHERE run_id = ?", runId);
    }

    @Override
    public void deleteVersion(UUID versionId) {
        jdbc.update("DELETE FROM data.ontology_relation_instance WHERE version_id = ?", versionId);
    }
}
