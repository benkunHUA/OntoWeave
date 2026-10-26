package com.ontoweave.data.ontology.index.switching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RelationKind;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.domain.OntologyObjectVersionEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationVersionEntity;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexEventPublisher;
import com.ontoweave.data.ontology.index.IndexFailureCode;
import com.ontoweave.data.ontology.index.pipeline.ObjectBuildResult;
import com.ontoweave.data.ontology.index.pipeline.RelationRunResult;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import com.ontoweave.data.ontology.repository.OntologyObjectBindingRepository;
import com.ontoweave.data.ontology.repository.OntologyObjectVersionRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationBindingRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationVersionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IndexSwitcher {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OntologyIndexRunRepository runs;
    private final OntologyObjectVersionRepository objectVersions;
    private final OntologyRelationVersionRepository relationVersions;
    private final OntologyObjectBindingRepository objectBindings;
    private final OntologyRelationBindingRepository relationBindings;
    private final IndexEventPublisher events;

    public IndexSwitcher(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            OntologyIndexRunRepository runs,
            OntologyObjectVersionRepository objectVersions,
            OntologyRelationVersionRepository relationVersions,
            OntologyObjectBindingRepository objectBindings,
            OntologyRelationBindingRepository relationBindings,
            IndexEventPublisher events
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.runs = runs;
        this.objectVersions = objectVersions;
        this.relationVersions = relationVersions;
        this.objectBindings = objectBindings;
        this.relationBindings = relationBindings;
        this.events = events;
    }

    @Transactional
    public void switchObject(
            OntologyIndexRunEntity run,
            String worker,
            ObjectBuildResult result
    ) {
        lockLease(run.getId(), worker);
        OntologyObjectVersionEntity objectVersion = objectVersions.findById(result.objectVersionId())
                .filter(value -> value.getRunId().equals(run.getId()))
                .filter(value -> value.getStatus() == com.ontoweave.data.ontology.common.VersionStatus.BUILDING)
                .orElseThrow(() -> failedSwitch("object version is not buildable"));
        List<OntologyRelationVersionEntity> activeRelations = new ArrayList<>();
        for (OntologyRelationVersionEntity relation : relationVersions.findByRunId(run.getId())) {
            if (!result.relations().stream().anyMatch(
                    built -> built.relationVersionId().equals(relation.getId()))) {
                continue;
            }
            if (relationStale(run, objectVersion.getId(), relation)) {
                discardRelation(relation);
            } else {
                activeRelations.add(relation);
            }
        }

        UUID previousObjectVersion = currentObjectVersion(run.getOwnerId(), run.getSubjectId());
        retireObject(previousObjectVersion, objectVersion.getId());
        if (jdbc.update("""
                UPDATE data.ontology_object_version
                SET status = 'ACTIVE', activated_at = ?
                WHERE id = ? AND status = 'BUILDING'
                """, Timestamp.from(Instant.now()), objectVersion.getId()) != 1) {
            throw failedSwitch("object version activation failed");
        }
        if (objectBindings.activateVersion(
                objectVersion.getBindingId(),
                objectVersion.getSourceDatasetKind(),
                objectVersion.getSourceDatasetId(),
                objectVersion.getSourceDatasetVersionId(),
                objectVersion.getMappingRevision(),
                objectVersion.getId()
        ) != 1) {
            throw failedSwitch("object binding update failed");
        }
        for (OntologyRelationVersionEntity relation : activeRelations) {
            activateRelation(run, relation);
        }
        complete(run, worker, "SUCCEEDED", result.stats(), null);
        events.succeeded(run, result.stats());
    }

    @Transactional
    public void switchRelation(
            OntologyIndexRunEntity run,
            String worker,
            RelationRunResult result
    ) {
        lockLease(run.getId(), worker);
        if (result.relation() == null) {
            complete(run, worker, "SUCCEEDED", result.stats(), null);
            events.succeeded(run, result.stats());
            return;
        }
        OntologyRelationVersionEntity relation = relationVersions
                .findById(result.relation().relationVersionId())
                .filter(value -> value.getRunId().equals(run.getId()))
                .filter(value -> value.getStatus() == com.ontoweave.data.ontology.common.VersionStatus.BUILDING)
                .orElseThrow(() -> failedSwitch("relation version is not buildable"));
        if (relationStale(run, null, relation)) {
            discardRelation(relation);
            complete(run, worker, "SUPERSEDED", result.stats(), IndexFailureCode.SUPERSEDED);
            events.failed(run, "SUPERSEDED", IndexFailureCode.SUPERSEDED, result.stats());
            return;
        }
        activateRelation(run, relation);
        complete(run, worker, "SUCCEEDED", result.stats(), null);
        events.succeeded(run, result.stats());
    }

    @Transactional
    public void completeWithoutVersion(
            OntologyIndexRunEntity run,
            String worker,
            Map<String, Object> stats
    ) {
        lockLease(run.getId(), worker);
        cleanupStages(run.getId());
        complete(run, worker, "SUCCEEDED", stats, null);
        events.succeeded(run, stats);
    }

    private void discardRelation(OntologyRelationVersionEntity relation) {
        int discarded = jdbc.update("""
                UPDATE data.ontology_relation_version SET status = 'DISCARDED'
                WHERE id = ? AND status = 'BUILDING'
                """, relation.getId());
        if (discarded == 1) {
            jdbc.update(
                    "DELETE FROM data.ontology_relation_instance WHERE version_id = ?",
                    relation.getId()
            );
        }
    }

    private void cleanupStages(UUID runId) {
        jdbc.update("DELETE FROM data.ontology_object_value_stage WHERE run_id = ?", runId);
        jdbc.update("DELETE FROM data.ontology_object_stage WHERE run_id = ?", runId);
        jdbc.update("DELETE FROM data.ontology_relation_stage WHERE run_id = ?", runId);
    }

    private void activateRelation(OntologyIndexRunEntity run, OntologyRelationVersionEntity relation) {
        UUID previous = null;
        if (relation.getBindingId() != null) {
            previous = currentRelationVersion(run.getOwnerId(), relation.getRelationId());
            retireRelation(previous, relation.getId());
        } else {
            retireForeignKeyVersions(run.getOwnerId(), relation.getRelationId(), relation.getId());
        }
        if (jdbc.update("""
                UPDATE data.ontology_relation_version
                SET status = 'ACTIVE', activated_at = ?
                WHERE id = ? AND status = 'BUILDING'
                """, Timestamp.from(Instant.now()), relation.getId()) != 1) {
            throw failedSwitch("relation version activation failed");
        }
        if (relation.getBindingId() != null && relationBindings.activateVersion(
                relation.getBindingId(),
                relation.getSourceDatasetKind(),
                relation.getSourceDatasetId(),
                relation.getSourceDatasetVersionId(),
                relation.getMappingRevision() == null ? 0L : relation.getMappingRevision(),
                relation.getId()
        ) != 1) {
            throw failedSwitch("relation binding update failed");
        }
    }

    private boolean relationStale(
            OntologyIndexRunEntity run,
            UUID rebuiltObjectVersionId,
            OntologyRelationVersionEntity relation
    ) {
        if (relation.getRelationKind() == RelationKind.FK) {
            return !endpointCurrent(run, relation.getFromObjectTypeId(),
                    relation.getFromObjectVersionId(), rebuiltObjectVersionId)
                    || !endpointCurrent(run, relation.getToObjectTypeId(),
                    relation.getToObjectVersionId(), rebuiltObjectVersionId);
        }
        List<UUID> activeDatasetVersions = jdbc.queryForList("""
                SELECT active_dataset_version_id FROM data.ontology_relation_binding
                WHERE relation_id = ? AND owner_id = ? AND status = 'ACTIVE'
                """, UUID.class, relation.getRelationId(), run.getOwnerId());
        UUID currentDatasetVersion = activeDatasetVersions.isEmpty()
                ? null
                : activeDatasetVersions.get(0);
        return !endpointCurrent(run, relation.getFromObjectTypeId(),
                relation.getFromObjectVersionId(), rebuiltObjectVersionId)
                || !endpointCurrent(run, relation.getToObjectTypeId(),
                relation.getToObjectVersionId(), rebuiltObjectVersionId)
                || (currentDatasetVersion != null
                && !relation.getSourceDatasetVersionId().equals(currentDatasetVersion));
    }

    private boolean endpointCurrent(
            OntologyIndexRunEntity run,
            UUID objectTypeId,
            UUID expectedVersion,
            UUID rebuiltObjectVersionId
    ) {
        if (rebuiltObjectVersionId != null && objectTypeId.equals(run.getSubjectId())) {
            return rebuiltObjectVersionId.equals(expectedVersion);
        }
        UUID current = currentObjectVersion(run.getOwnerId(), objectTypeId);
        return expectedVersion.equals(current);
    }

    private void retireObject(UUID previous, UUID next) {
        if (previous != null && !previous.equals(next)) {
            jdbc.update("""
                    UPDATE data.ontology_object_version
                    SET status = 'RETIRED', retired_at = ?
                    WHERE id = ? AND status = 'ACTIVE'
                    """, Timestamp.from(Instant.now()), previous);
        }
    }

    private void retireRelation(UUID previous, UUID next) {
        if (previous != null && !previous.equals(next)) {
            jdbc.update("""
                    UPDATE data.ontology_relation_version
                    SET status = 'RETIRED', retired_at = ?
                    WHERE id = ? AND status = 'ACTIVE'
                    """, Timestamp.from(Instant.now()), previous);
        }
    }

    private void retireForeignKeyVersions(UUID ownerId, UUID relationId, UUID next) {
        jdbc.update("""
                UPDATE data.ontology_relation_version
                SET status = 'RETIRED', retired_at = ?
                WHERE owner_id = ? AND relation_id = ? AND relation_kind = 'FK'
                  AND status = 'ACTIVE' AND id <> ?
                """, Timestamp.from(Instant.now()), ownerId, relationId, next);
    }

    private UUID currentObjectVersion(UUID ownerId, UUID objectTypeId) {
        return objectBindings.findByObjectTypeIdAndOwnerId(objectTypeId, ownerId)
                .map(value -> value.getCurrentIndexVersionId())
                .orElse(null);
    }

    private UUID currentRelationVersion(UUID ownerId, UUID relationId) {
        return relationBindings.findByRelationIdAndOwnerId(relationId, ownerId)
                .map(value -> value.getCurrentIndexVersionId())
                .orElse(null);
    }

    private void lockLease(UUID runId, String worker) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT status, lease_owner FROM data.ontology_index_run
                WHERE id = ? FOR UPDATE
                """, runId);
        if (rows.isEmpty()
                || !"RUNNING".equals(rows.get(0).get("status"))
                || !worker.equals(rows.get(0).get("lease_owner"))) {
            throw failedSwitch("run lease is no longer owned by this worker");
        }
    }

    private void complete(
            OntologyIndexRunEntity run,
            String worker,
            String status,
            Map<String, Object> stats,
            String errorCode
    ) {
        int updated = jdbc.update("""
                UPDATE data.ontology_index_run
                SET status = ?, phase = NULL, finished_at = now(), stats = CAST(? AS jsonb),
                    error_code = ?, lease_owner = NULL, lease_expires_at = NULL
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, status, json(stats), errorCode, run.getId(), worker);
        if (updated != 1) {
            throw failedSwitch("run terminal update failed");
        }
    }

    private IndexBuildException failedSwitch(String message) {
        return new IndexBuildException(IndexFailureCode.LEASE_LOST, message);
    }

    private String json(Map<String, Object> stats) {
        try {
            return mapper.writeValueAsString(stats == null ? Map.of() : stats);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
