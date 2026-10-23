package com.ontoweave.data.ontology.index.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RelationKind;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexFailureCode;
import com.ontoweave.data.ontology.store.RelationStoreWriter;
import com.ontoweave.data.ontology.store.StageValidation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class RelationMaterializer {

    private final JdbcTemplate jdbc;
    private final RelationStoreWriter writer;

    public RelationMaterializer(JdbcTemplate jdbc, RelationStoreWriter writer) {
        this.jdbc = jdbc;
        this.writer = writer;
    }

    public RelationBuildResult materialize(
            UUID runId,
            UUID ownerId,
            UUID bindingId,
            UUID relationId,
            RelationKind kind,
            EndpointVersion from,
            EndpointVersion to,
            DatasetKind sourceDatasetKind,
            UUID sourceDatasetId,
            UUID sourceDatasetVersionId,
            Long sourceSnapshotId,
            JsonNode mappingSnapshot,
            Long mappingRevision,
            long skippedCount
    ) {
        StageValidation validation = writer.validate(runId);
        if (!validation.valid()) {
            throw new IndexBuildException(
                    IndexFailureCode.STAGE_VALIDATION_FAILED,
                    "relation stage validation failed",
                    Map.of(
                            "rowCount", validation.rowCount(),
                            "missingKeys", validation.requiredMissingCount(),
                            "samples", validation.samples()
                    )
            );
        }
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_version
                    (id, owner_id, binding_id, relation_id, relation_kind,
                     from_object_type_id, to_object_type_id,
                     from_object_version_id, to_object_version_id,
                     source_dataset_kind, source_dataset_id, source_dataset_version_id,
                     source_snapshot_id, mapping_snapshot, mapping_revision,
                     run_id, status, row_count, skipped_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?,
                        'BUILDING', ?, ?)
                """, versionId, ownerId, bindingId, relationId, kind.name(),
                from.objectTypeId(), to.objectTypeId(), from.versionId(), to.versionId(),
                sourceDatasetKind == null ? null : sourceDatasetKind.name(), sourceDatasetId,
                sourceDatasetVersionId, sourceSnapshotId,
                mappingSnapshot == null ? null : mappingSnapshot.toString(),
                mappingRevision, runId, validation.rowCount(), skippedCount);
        writer.promote(runId, versionId);
        writer.deleteStage(runId);
        return new RelationBuildResult(
                versionId,
                relationId,
                validation.rowCount(),
                skippedCount
        );
    }
}
