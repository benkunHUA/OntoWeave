package com.ontoweave.data.ontology.index.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.RunPhase;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.index.IndexEventPublisher;
import com.ontoweave.data.ontology.index.OntologyIndexProperties;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class OntologyRunStateStore {

    private final OntologyIndexRunRepository runs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IndexEventPublisher events;
    private final OntologyIndexProperties properties;

    public OntologyRunStateStore(
            OntologyIndexRunRepository runs,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            IndexEventPublisher events,
            OntologyIndexProperties properties
    ) {
        this.runs = runs;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.events = events;
        this.properties = properties;
    }

    @Transactional
    public Optional<OntologyIndexRunEntity> claimNext(String worker) {
        List<UUID> ids = jdbc.queryForList("""
                SELECT id FROM data.ontology_index_run
                WHERE status = 'QUEUED'
                ORDER BY CASE subject_kind WHEN 'OBJECT_TYPE' THEN 0 ELSE 1 END,
                         requested_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """, UUID.class);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        UUID id = ids.get(0);
        String lease = properties.leaseDuration().toMillis() + " milliseconds";
        int claimed = jdbc.update("""
                UPDATE data.ontology_index_run
                SET status = 'RUNNING', phase = 'READING', started_at = now(),
                    heartbeat_at = now(), lease_owner = ?,
                    lease_expires_at = now() + CAST(? AS interval),
                    error_code = NULL, error_message = NULL, error_details = NULL
                WHERE id = ? AND status = 'QUEUED'
                """, worker, lease, id);
        if (claimed != 1) {
            return Optional.empty();
        }
        OntologyIndexRunEntity run = runs.findById(id).orElseThrow();
        supersedeAffectedRelationRuns(run);
        events.started(run);
        return Optional.of(run);
    }

    public boolean heartbeat(UUID runId, String worker, RunPhase phase) {
        Instant expiresAt = Instant.now().plus(properties.leaseDuration());
        return runs.heartbeat(runId, worker, phase, expiresAt) == 1;
    }

    public boolean renewLease(UUID runId, String worker) {
        String lease = properties.leaseDuration().toMillis() + " milliseconds";
        return jdbc.update("""
                UPDATE data.ontology_index_run
                SET heartbeat_at = now(), lease_expires_at = now() + CAST(? AS interval)
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, lease, runId, worker) == 1;
    }

    public boolean cancelRequested(UUID runId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_index_run
                WHERE id = ? AND status = 'RUNNING' AND cancel_requested_at IS NOT NULL
                """, Integer.class, runId);
        return count != null && count > 0;
    }

    @Transactional
    public CancelOutcome cancel(UUID runId) {
        OntologyIndexRunEntity run = lockRun(runId);
        if (run == null) {
            return CancelOutcome.NOT_CANCELLABLE;
        }
        if (run.getStatus() == RunStatus.QUEUED) {
            jdbc.update("""
                    UPDATE data.ontology_index_run
                    SET status = 'CANCELLED', phase = NULL, finished_at = now()
                    WHERE id = ? AND status = 'QUEUED'
                    """, runId);
            events.cancelled(run, Map.of());
            return CancelOutcome.QUEUED_CANCELLED;
        }
        if (run.getStatus() == RunStatus.RUNNING && run.getCancelRequestedAt() == null) {
            jdbc.update("""
                    UPDATE data.ontology_index_run
                    SET cancel_requested_at = now()
                    WHERE id = ? AND status = 'RUNNING' AND cancel_requested_at IS NULL
                    """, runId);
            return CancelOutcome.RUNNING_REQUESTED;
        }
        return CancelOutcome.NOT_CANCELLABLE;
    }

    @Transactional
    public int recoverExpired(int limit, Instant now) {
        List<OntologyIndexRunEntity> expired = runs.findExpiredLeases(now, limit);
        int recovered = 0;
        for (OntologyIndexRunEntity candidate : expired) {
            int updated = jdbc.update("""
                    UPDATE data.ontology_index_run
                    SET status = 'FAILED', phase = NULL, finished_at = now(),
                        error_code = 'WORKER_LOST', error_message = :message,
                        lease_owner = NULL, lease_expires_at = NULL
                    WHERE id = :id AND status = 'RUNNING' AND lease_expires_at < :now
                    """.replace(":message", "?").replace(":id", "?").replace(":now", "?"),
                    "worker lease expired",
                    candidate.getId(),
                    Timestamp.from(now)
            );
            if (updated == 1) {
                cleanupRunArtifacts(candidate.getId());
                events.failed(candidate, "FAILED", "WORKER_LOST", Map.of());
                recovered++;
            }
        }
        return recovered;
    }

    @Transactional
    public boolean fail(
            OntologyIndexRunEntity run,
            String worker,
            String errorCode,
            String message,
            Map<String, Object> details,
            Map<String, Object> stats
    ) {
        int updated = jdbc.update("""
                UPDATE data.ontology_index_run
                SET status = 'FAILED', phase = NULL, finished_at = now(),
                    error_code = ?, error_message = ?, error_details = CAST(? AS jsonb),
                    stats = CAST(? AS jsonb), lease_owner = NULL, lease_expires_at = NULL
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, errorCode, message, json(details), json(stats), run.getId(), worker);
        if (updated != 1) {
            return false;
        }
        cleanupRunArtifacts(run.getId());
        events.failed(run, "FAILED", errorCode, stats);
        return true;
    }

    @Transactional
    public boolean cancelExhausted(
            OntologyIndexRunEntity run,
            String worker,
            Map<String, Object> stats
    ) {
        int updated = jdbc.update("""
                UPDATE data.ontology_index_run
                SET status = 'CANCELLED', phase = NULL, finished_at = now(),
                    stats = CAST(? AS jsonb), lease_owner = NULL, lease_expires_at = NULL
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, json(stats), run.getId(), worker);
        if (updated != 1) {
            return false;
        }
        cleanupRunArtifacts(run.getId());
        events.cancelled(run, stats);
        return true;
    }

    @Transactional
    public boolean markSuperseded(
            OntologyIndexRunEntity run,
            String worker,
            String message,
            Map<String, Object> stats
    ) {
        int updated = jdbc.update("""
                UPDATE data.ontology_index_run
                SET status = 'SUPERSEDED', phase = NULL, finished_at = now(),
                    error_code = 'SUPERSEDED', error_message = ?,
                    stats = CAST(? AS jsonb), lease_owner = NULL, lease_expires_at = NULL
                WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?
                """, message, json(stats), run.getId(), worker);
        if (updated != 1) {
            return false;
        }
        cleanupRunArtifacts(run.getId());
        events.failed(run, "SUPERSEDED", "SUPERSEDED", stats);
        return true;
    }

    public void cleanupStages(UUID runId) {
        jdbc.update("DELETE FROM data.ontology_object_value_stage WHERE run_id = ?", runId);
        jdbc.update("DELETE FROM data.ontology_object_stage WHERE run_id = ?", runId);
        jdbc.update("DELETE FROM data.ontology_relation_stage WHERE run_id = ?", runId);
    }

    public void cleanupRunArtifacts(UUID runId) {
        cleanupStages(runId);
        jdbc.update("""
                DELETE FROM data.ontology_object_value
                WHERE version_id IN (
                    SELECT id FROM data.ontology_object_version
                    WHERE run_id = ? AND status <> 'ACTIVE'
                )
                """, runId);
        jdbc.update("""
                DELETE FROM data.ontology_object_instance
                WHERE version_id IN (
                    SELECT id FROM data.ontology_object_version
                    WHERE run_id = ? AND status <> 'ACTIVE'
                )
                """, runId);
        jdbc.update("""
                DELETE FROM data.ontology_relation_instance
                WHERE version_id IN (
                    SELECT id FROM data.ontology_relation_version
                    WHERE run_id = ? AND status <> 'ACTIVE'
                )
                """, runId);
        jdbc.update("DELETE FROM data.ontology_relation_version WHERE run_id = ? AND status <> 'ACTIVE'", runId);
        jdbc.update("DELETE FROM data.ontology_object_version WHERE run_id = ? AND status <> 'ACTIVE'", runId);
    }

    private void supersedeAffectedRelationRuns(OntologyIndexRunEntity run) {
        if (run.getSubjectKind() != com.ontoweave.data.ontology.common.SubjectKind.OBJECT_TYPE) {
            return;
        }
        jdbc.update("""
                UPDATE data.ontology_index_run r
                SET status = 'SUPERSEDED', phase = NULL, finished_at = now(),
                    error_code = 'SUPERSEDED',
                    error_message = 'object type rebuild superseded this relation run',
                    stats = CAST(? AS jsonb)
                WHERE r.status = 'QUEUED' AND r.subject_kind = 'RELATION'
                  AND r.subject_id IN (
                      SELECT relation_id FROM data.ontology_relation_definition
                      WHERE owner_id = ? AND (source_type_id = ? OR target_type_id = ?)
                  )
                """, json(Map.of("supersededByRunId", run.getId())),
                run.getOwnerId(), run.getSubjectId(), run.getSubjectId());
    }

    private OntologyIndexRunEntity lockRun(UUID runId) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM data.ontology_index_run WHERE id = ? FOR UPDATE",
                UUID.class,
                runId
        );
        return ids.isEmpty() ? null : runs.findById(runId).orElse(null);
    }

    private String json(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    public enum CancelOutcome {
        QUEUED_CANCELLED,
        RUNNING_REQUESTED,
        NOT_CANCELLABLE
    }
}
