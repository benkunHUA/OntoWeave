package com.ontoweave.data.ontology.index.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.RunPhase;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexCancelledException;
import com.ontoweave.data.ontology.index.IndexFailureCode;
import com.ontoweave.data.ontology.index.OntologyIndexProperties;
import com.ontoweave.data.ontology.index.queue.RunDatasetResolver;
import com.ontoweave.data.ontology.index.run.OntologyRunStateStore;
import com.ontoweave.data.ontology.store.ObjectStoreWriter;
import com.ontoweave.data.ontology.store.StageValidation;
import org.apache.iceberg.data.Record;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ObjectIndexPipeline {

    private final RunDatasetResolver datasets;
    private final IcebergSnapshotReader snapshots;
    private final ObjectRowMapper rowMapper;
    private final ObjectStoreWriter store;
    private final ObjectStageValidator stageValidator;
    private final RelationIndexBuilder relations;
    private final OntologyRunStateStore stateStore;
    private final OntologyIndexProperties properties;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public ObjectIndexPipeline(
            RunDatasetResolver datasets,
            IcebergSnapshotReader snapshots,
            ObjectRowMapper rowMapper,
            ObjectStoreWriter store,
            ObjectStageValidator stageValidator,
            RelationIndexBuilder relations,
            OntologyRunStateStore stateStore,
            OntologyIndexProperties properties,
            ObjectMapper mapper,
            JdbcTemplate jdbc
    ) {
        this.datasets = datasets;
        this.snapshots = snapshots;
        this.rowMapper = rowMapper;
        this.store = store;
        this.stageValidator = stageValidator;
        this.relations = relations;
        this.stateStore = stateStore;
        this.properties = properties;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    public ObjectBuildResult execute(OntologyIndexRunEntity run, String worker) {
        DatasetRef dataset = datasets.require(
                run.getTargetDatasetKind(), run.getTargetDatasetVersionId(), run.getOwnerId());
        MappingSnapshot mapping = parse(run);
        UUID objectVersionId = createVersion(run, dataset);
        store.deleteStage(run.getId());
        List<String> conversionSamples = new ArrayList<>();
        long rows = readAndStage(run, dataset, mapping, conversionSamples, worker);
        if (!conversionSamples.isEmpty()) {
            throw new IndexBuildException(
                    IndexFailureCode.STAGE_VALIDATION_FAILED,
                    "object row validation failed",
                    Map.of("samples", conversionSamples.subList(0, Math.min(20, conversionSamples.size())))
            );
        }
        checkpoint(run, worker, RunPhase.VALIDATING);
        StageValidation validation = stageValidator.validate(run.getId(), mapping, store);
        if (!validation.valid()) {
            throw new IndexBuildException(
                    IndexFailureCode.STAGE_VALIDATION_FAILED,
                    "object stage validation failed",
                    Map.of(
                            "rowCount", validation.rowCount(),
                            "duplicateKeyCount", validation.duplicateKeyCount(),
                            "requiredMissingCount", validation.requiredMissingCount(),
                            "samples", validation.samples()
                    )
            );
        }
        checkpoint(run, worker, RunPhase.WRITING);
        store.promote(run.getId(), objectVersionId);
        jdbc.update("""
                UPDATE data.ontology_object_version
                SET row_count = ?, warning_count = ?
                WHERE id = ? AND run_id = ?
                """, rows, 0, objectVersionId, run.getId());
        RelationBuildBatch relationBatch = relations.buildForObject(
                run,
                objectVersionId,
                mapping,
                dataset,
                () -> checkpoint(run, worker, RunPhase.WRITING)
        );
        Map<String, Object> stats = stats(rows, relationBatch);
        return new ObjectBuildResult(
                objectVersionId,
                rows,
                0,
                relationBatch.relations(),
                stats
        );
    }

    private long readAndStage(
            OntologyIndexRunEntity run,
            DatasetRef dataset,
            MappingSnapshot mapping,
            List<String> conversionSamples,
            String worker
    ) {
        long rows = 0;
        try (IcebergSnapshotReader.SnapshotRead read = snapshots.open(dataset, dataset.snapshotId())) {
            List<ObjectStoreWriter.ObjectStageRow> batch = new ArrayList<>(properties.batchSize());
            for (Record record : read.records()) {
                try {
                    batch.add(rowMapper.map(record, run.getSubjectId(), mapping));
                    rows++;
                } catch (IndexBuildException exception) {
                    if (!isRowFailure(exception)) {
                        throw exception;
                    }
                    if (conversionSamples.size() < 20) {
                        conversionSamples.add(exception.errorCode() + ": " + exception.getMessage());
                    }
                }
                if (batch.size() >= properties.batchSize()) {
                    store.write(run.getId(), batch);
                    batch.clear();
                    checkpoint(run, worker, RunPhase.READING);
                }
            }
            if (!batch.isEmpty()) {
                store.write(run.getId(), batch);
            }
        }
        return rows;
    }

    private boolean isRowFailure(IndexBuildException exception) {
        return IndexFailureCode.TYPE_CONVERSION_FAILED.equals(exception.errorCode())
                || IndexFailureCode.ENUM_OUT_OF_RANGE.equals(exception.errorCode());
    }

    private void checkpoint(OntologyIndexRunEntity run, String worker, RunPhase phase) {
        if (stateStore.cancelRequested(run.getId())) {
            throw new IndexCancelledException();
        }
        if (!stateStore.heartbeat(run.getId(), worker, phase)) {
            throw new IndexBuildException(
                    IndexFailureCode.LEASE_LOST,
                    "index run lease is no longer owned by this worker"
            );
        }
    }

    private UUID createVersion(OntologyIndexRunEntity run, DatasetRef dataset) {
        if (run.getBindingId() == null) {
            throw new IndexBuildException(
                    IndexFailureCode.MAPPING_INVALID,
                    "object run has no binding"
            );
        }
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_object_version
                    (id, owner_id, binding_id, object_type_id, source_dataset_kind,
                     source_dataset_id, source_dataset_version_id, source_snapshot_id,
                     mapping_snapshot, mapping_revision, run_id, status, warning_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, 'BUILDING', 0)
                """, versionId, run.getOwnerId(), run.getBindingId(), run.getSubjectId(),
                dataset.kind().name(), dataset.datasetId(), dataset.versionId(), dataset.snapshotId(),
                run.getMappingSnapshot().toString(), run.getMappingRevision(), run.getId());
        return versionId;
    }

    private MappingSnapshot parse(OntologyIndexRunEntity run) {
        if (run.getMappingSnapshot() == null || run.getMappingSnapshot().isNull()) {
            throw new IndexBuildException(
                    IndexFailureCode.MAPPING_INVALID,
                    "run mapping snapshot is empty"
            );
        }
        try {
            return mapper.treeToValue(run.getMappingSnapshot(), MappingSnapshot.class);
        } catch (Exception exception) {
            throw new IndexBuildException(
                    IndexFailureCode.MAPPING_INVALID,
                    "run mapping snapshot is invalid",
                    Map.of(),
                    exception
            );
        }
    }

    private Map<String, Object> stats(long rows, RelationBuildBatch relationBatch) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("objectRowCount", rows);
        stats.put("objectWarningCount", 0);
        stats.put("relations", relationBatch.relations().stream().map(result -> Map.of(
                "relationId", result.relationId(),
                "rowCount", result.rowCount(),
                "skippedCount", result.skippedCount()
        )).toList());
        if (!relationBatch.skippedRelationIds().isEmpty()) {
            stats.put("skippedRelationIds", relationBatch.skippedRelationIds());
        }
        return stats;
    }
}
