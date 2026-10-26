package com.ontoweave.data.ontology.index.store;

import com.ontoweave.data.ontology.index.OntologyIndexProperties;
import com.ontoweave.data.ontology.store.ObjectStoreWriter;
import com.ontoweave.data.ontology.store.StageValidation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class JdbcObjectStoreWriter implements ObjectStoreWriter {

    private static final int[] OBJECT_TYPES = {
            Types.OTHER, Types.OTHER, Types.VARCHAR, Types.NUMERIC,
            Types.DATE, Types.TIMESTAMP_WITH_TIMEZONE, Types.VARCHAR, Types.OTHER
    };
    private static final int[] VALUE_TYPES = {
            Types.OTHER, Types.OTHER, Types.VARCHAR, Types.VARCHAR,
            Types.NUMERIC, Types.DATE, Types.TIMESTAMP_WITH_TIMEZONE, Types.BOOLEAN
    };

    private final JdbcTemplate jdbc;
    private final int batchSize;

    public JdbcObjectStoreWriter(JdbcTemplate jdbc, OntologyIndexProperties properties) {
        this.jdbc = jdbc;
        this.batchSize = properties.batchSize();
    }

    @Override
    public void write(UUID runId, List<ObjectStageRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> objectBatch = new ArrayList<>(rows.size());
        List<Object[]> valueBatch = new ArrayList<>();
        for (ObjectStageRow row : rows) {
            objectBatch.add(new Object[]{
                    runId,
                    row.objectTypeId(),
                    row.primaryKeyValue(),
                    row.primaryKeyNum(),
                    row.primaryKeyDate(),
                    timestamp(row.primaryKeyTs()),
                    row.titleValue(),
                    row.properties() == null ? "{}" : row.properties().toString()
            });
            for (ObjectValueStageRow value : row.values()) {
                valueBatch.add(new Object[]{
                        runId,
                        value.attributeId(),
                        value.primaryKeyValue(),
                        value.textValue(),
                        value.numValue(),
                        value.dateValue(),
                        timestamp(value.tsValue()),
                        value.boolValue()
                });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO data.ontology_object_stage
                    (run_id, object_type_id, primary_key_value, primary_key_num,
                     primary_key_date, primary_key_ts, title_value, properties)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, objectBatch, OBJECT_TYPES);
        if (!valueBatch.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO data.ontology_object_value_stage
                        (run_id, attribute_id, primary_key_value, text_value, num_value,
                         date_value, ts_value, bool_value)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, valueBatch, VALUE_TYPES);
        }
    }

    @Override
    public StageValidation validate(UUID runId) {
        Long rowCount = count(runId);
        Long missing = jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_stage
                WHERE run_id = ? AND (primary_key_value IS NULL OR btrim(primary_key_value) = '')
                """, Long.class, runId);
        Long duplicates = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT primary_key_value FROM data.ontology_object_stage
                    WHERE run_id = ? AND primary_key_value IS NOT NULL
                    GROUP BY primary_key_value HAVING count(*) > 1
                ) duplicate_keys
                """, Long.class, runId);
        List<String> samples = jdbc.queryForList("""
                SELECT primary_key_value FROM data.ontology_object_stage
                WHERE run_id = ? AND primary_key_value IS NOT NULL
                GROUP BY primary_key_value HAVING count(*) > 1
                LIMIT 10
                """, String.class, runId).stream().map(value -> "duplicate: " + value).toList();
        return new StageValidation(
                (missing == null || missing == 0) && (duplicates == null || duplicates == 0),
                rowCount == null ? 0 : rowCount,
                duplicates == null ? 0 : duplicates,
                missing == null ? 0 : missing,
                samples
        );
    }

    @Override
    public void promote(UUID runId, UUID versionId) {
        int promoted;
        do {
            promoted = jdbc.update("""
                    WITH batch AS (
                        DELETE FROM data.ontology_object_stage
                        WHERE ctid IN (
                            SELECT ctid FROM data.ontology_object_stage
                            WHERE run_id = ? ORDER BY ctid LIMIT ?
                        )
                        RETURNING object_type_id, primary_key_value, primary_key_num,
                                  primary_key_date, primary_key_ts, title_value, properties
                    )
                    INSERT INTO data.ontology_object_instance
                        (version_id, object_type_id, primary_key_value, primary_key_num,
                         primary_key_date, primary_key_ts, title_value, properties)
                    SELECT ?, object_type_id, primary_key_value, primary_key_num,
                           primary_key_date, primary_key_ts, title_value, properties
                    FROM batch
                    ON CONFLICT DO NOTHING
                    """, runId, batchSize, versionId);
        } while (promoted == batchSize);

        do {
            promoted = jdbc.update("""
                    WITH batch AS (
                        DELETE FROM data.ontology_object_value_stage
                        WHERE ctid IN (
                            SELECT ctid FROM data.ontology_object_value_stage
                            WHERE run_id = ? ORDER BY ctid LIMIT ?
                        )
                        RETURNING attribute_id, primary_key_value, text_value, num_value,
                                  date_value, ts_value, bool_value
                    )
                    INSERT INTO data.ontology_object_value
                        (version_id, attribute_id, primary_key_value, text_value, num_value,
                         date_value, ts_value, bool_value)
                    SELECT ?, attribute_id, primary_key_value, text_value, num_value,
                           date_value, ts_value, bool_value
                    FROM batch
                    WHERE primary_key_value IS NOT NULL
                    ON CONFLICT DO NOTHING
                    """, runId, batchSize, versionId);
        } while (promoted == batchSize);
    }

    @Override
    public void deleteStage(UUID runId) {
        jdbc.update("DELETE FROM data.ontology_object_value_stage WHERE run_id = ?", runId);
        jdbc.update("DELETE FROM data.ontology_object_stage WHERE run_id = ?", runId);
    }

    @Override
    public void deleteVersion(UUID versionId) {
        jdbc.update("DELETE FROM data.ontology_object_value WHERE version_id = ?", versionId);
        jdbc.update("DELETE FROM data.ontology_object_instance WHERE version_id = ?", versionId);
    }

    private Long count(UUID runId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_object_stage WHERE run_id = ?",
                Long.class,
                runId
        );
    }

    private OffsetDateTime timestamp(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
