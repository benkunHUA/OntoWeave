package com.ontoweave.data.ontology.index.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.RelationKind;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.iceberg.ColumnValueReader;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.ontology.index.OntologyIndexProperties;
import com.ontoweave.data.ontology.store.RelationStoreWriter;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ManyToManyRelationBuilder {

    private final IcebergSnapshotReader snapshots;
    private final ColumnValueReader reader = new ColumnValueReader();
    private final RelationStoreWriter writer;
    private final RelationMaterializer materializer;
    private final JdbcTemplate jdbc;
    private final int batchSize;

    public ManyToManyRelationBuilder(
            IcebergSnapshotReader snapshots,
            RelationStoreWriter writer,
            RelationMaterializer materializer,
            JdbcTemplate jdbc,
            OntologyIndexProperties properties
    ) {
        this.snapshots = snapshots;
        this.writer = writer;
        this.materializer = materializer;
        this.jdbc = jdbc;
        this.batchSize = properties.batchSize();
    }

    public RelationBuildResult build(
            UUID runId,
            UUID ownerId,
            UUID bindingId,
            OntologyRelationDefinitionEntity definition,
            MappingSnapshot mapping,
            JsonNode mappingSnapshot,
            long mappingRevision,
            DatasetRef dataset,
            EndpointVersion from,
            EndpointVersion to,
            Runnable checkpoint
    ) {
        if (mapping == null || !mapping.isRelationMapping()) {
            throw new RelationSkippedException("MAPPING_MISSING", "relation mapping is unavailable");
        }
        writer.deleteStage(runId);
        long skipped = 0;
        try (IcebergSnapshotReader.SnapshotRead read = snapshots.open(dataset, dataset.snapshotId())) {
            List<RelationStoreWriter.RelationStageRow> batch = new ArrayList<>(batchSize);
            for (Record record : read.records()) {
                Object fromRaw = read(record, mapping.fromKeyColumn());
                Object toRaw = read(record, mapping.toKeyColumn());
                if (fromRaw == null || toRaw == null) {
                    skipped++;
                    continue;
                }
                batch.add(new RelationStoreWriter.RelationStageRow(
                        definition.getRelationId(),
                        from.objectTypeId(),
                        IndexValueCodec.canonicalKey(fromRaw, mapping.fromKeyDatatype()),
                        to.objectTypeId(),
                        IndexValueCodec.canonicalKey(toRaw, mapping.toKeyDatatype())
                ));
                if (batch.size() >= batchSize) {
                    writer.write(runId, batch);
                    batch.clear();
                    checkpoint.run();
                }
            }
            if (!batch.isEmpty()) {
                writer.write(runId, batch);
            }
        } catch (IcebergSnapshotReader.IcebergSnapshotNotFoundException exception) {
            throw new RelationSkippedException("SNAPSHOT_MISSING", exception.getMessage());
        }
        checkpoint.run();
        skipped += removeMissingEndpoints(runId, definition.getRelationId(), from, to);
        skipped += removeDuplicates(runId, definition.getRelationId());
        return materializer.materialize(
                runId,
                ownerId,
                bindingId,
                definition.getRelationId(),
                RelationKind.MANY_TO_MANY,
                from,
                to,
                dataset.kind(),
                dataset.datasetId(),
                dataset.versionId(),
                dataset.snapshotId(),
                mappingSnapshot,
                mappingRevision,
                skipped
        );
    }

    private long removeMissingEndpoints(
            UUID runId,
            UUID relationId,
            EndpointVersion from,
            EndpointVersion to
    ) {
        String predicate = """
                rs.run_id = ? AND rs.relation_id = ?
                AND (
                    NOT EXISTS (
                        SELECT 1 FROM data.ontology_object_instance s
                        WHERE s.version_id = ? AND s.primary_key_value = rs.from_primary_key
                    )
                    OR NOT EXISTS (
                        SELECT 1 FROM data.ontology_object_instance t
                        WHERE t.version_id = ? AND t.primary_key_value = rs.to_primary_key
                    )
                )
                """;
        Long missing = jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_relation_stage rs WHERE " + predicate,
                Long.class,
                runId,
                relationId,
                from.versionId(),
                to.versionId()
        );
        jdbc.update(
                "DELETE FROM data.ontology_relation_stage rs WHERE " + predicate,
                runId,
                relationId,
                from.versionId(),
                to.versionId()
        );
        return missing == null ? 0 : missing;
    }

    private long removeDuplicates(UUID runId, UUID relationId) {
        Long duplicates = jdbc.queryForObject("""
                SELECT COALESCE(sum(duplicate_count - 1), 0) FROM (
                    SELECT count(*) AS duplicate_count
                    FROM data.ontology_relation_stage
                    WHERE run_id = ? AND relation_id = ?
                    GROUP BY from_primary_key, to_primary_key
                    HAVING count(*) > 1
                ) duplicates
                """, Long.class, runId, relationId);
        jdbc.update("""
                DELETE FROM data.ontology_relation_stage a
                USING data.ontology_relation_stage b
                WHERE a.run_id = ? AND b.run_id = ?
                  AND a.relation_id = ? AND b.relation_id = ?
                  AND a.from_primary_key = b.from_primary_key
                  AND a.to_primary_key = b.to_primary_key
                  AND a.ctid > b.ctid
                """, runId, runId, relationId, relationId);
        return duplicates == null ? 0 : duplicates;
    }

    private Object read(Record record, String column) {
        if (column == null || column.isBlank()) {
            return null;
        }
        return reader.read(record, column);
    }
}
