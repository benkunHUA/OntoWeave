package com.ontoweave.data.ontology.index;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RunPhase;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.index.run.OntologyRunStateStore;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyIndexWorkerIntegrationTest extends OntologyIndexIntegrationTestSupport {

    @Autowired
    private OntologyRunStateStore stateStore;

    @Autowired
    private OntologyIndexRunRepository runs;

    @Test
    void claimsObjectRunsBeforeRelationRunsAndSetsTheLease() {
        UUID relationRun = queue(SubjectKind.RELATION, UUID.randomUUID());
        UUID objectRun = queue(SubjectKind.OBJECT_TYPE, UUID.randomUUID());
        jdbc.update("UPDATE data.ontology_index_run SET requested_at = now() - interval '1 minute' WHERE id = ?",
                relationRun);

        OntologyIndexRunEntity claimed = stateStore.claimNext("worker-a").orElseThrow();

        assertThat(claimed.getId()).isEqualTo(objectRun);
        assertThat(claimed.getStatus()).isEqualTo(RunStatus.RUNNING);
        assertThat(claimed.getPhase()).isEqualTo(RunPhase.READING);
        assertThat(claimed.getLeaseOwner()).isEqualTo("worker-a");
        assertThat(claimed.getLeaseExpiresAt()).isAfter(Instant.now().plusSeconds(50));
    }

    @Test
    void concurrentWorkersClaimDifferentRuns() {
        UUID first = queue(SubjectKind.OBJECT_TYPE, UUID.randomUUID());
        UUID second = queue(SubjectKind.OBJECT_TYPE, UUID.randomUUID());

        UUID claimedA = stateStore.claimNext("worker-a").orElseThrow().getId();
        UUID claimedB = stateStore.claimNext("worker-b").orElseThrow().getId();

        assertThat(claimedA).isNotEqualTo(claimedB);
        assertThat(claimedA).isIn(first, second);
        assertThat(claimedB).isIn(first, second);
    }

    @Test
    void renewsAndRecoversAnExpiredLease() {
        UUID runId = queue(SubjectKind.OBJECT_TYPE, UUID.randomUUID());
        OntologyIndexRunEntity run = stateStore.claimNext("worker-a").orElseThrow();
        assertThat(stateStore.renewLease(runId, "worker-a")).isTrue();
        jdbc.update("""
                INSERT INTO data.ontology_object_stage
                    (run_id, object_type_id, primary_key_value, properties)
                VALUES (?, ?, '1', '{}'::jsonb)
                """, runId, run.getSubjectId());
        jdbc.update("""
                UPDATE data.ontology_index_run
                SET lease_expires_at = now() - interval '1 second' WHERE id = ?
                """, runId);

        int recovered = stateStore.recoverExpired(10, Instant.now());

        assertThat(recovered).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM data.ontology_index_run WHERE id = ?
                """, String.class, runId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("""
                SELECT error_code FROM data.ontology_index_run WHERE id = ?
                """, String.class, runId)).isEqualTo("WORKER_LOST");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_stage WHERE run_id = ?
                """, Integer.class, runId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.outbox_event
                WHERE aggregate_id = ? AND event_type = 'ontology.index.run.failed'
                """, Integer.class, runId)).isEqualTo(1);
    }

    @Test
    void cancelsQueuedRunsImmediatelyAndRequestsRunningRunsToStop() {
        UUID queuedId = queue(SubjectKind.OBJECT_TYPE, UUID.randomUUID());
        assertThat(stateStore.cancel(queuedId))
                .isEqualTo(OntologyRunStateStore.CancelOutcome.QUEUED_CANCELLED);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.ontology_index_run WHERE id = ?",
                String.class, queuedId)).isEqualTo("CANCELLED");

        UUID runningId = queue(SubjectKind.OBJECT_TYPE, UUID.randomUUID());
        OntologyIndexRunEntity running = stateStore.claimNext("worker-a").orElseThrow();
        assertThat(stateStore.cancel(runningId))
                .isEqualTo(OntologyRunStateStore.CancelOutcome.RUNNING_REQUESTED);
        assertThat(stateStore.cancelRequested(runningId)).isTrue();
        assertThat(stateStore.cancelExhausted(running, "worker-a", java.util.Map.of("cancelled", true)))
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.ontology_index_run WHERE id = ?",
                String.class, runningId)).isEqualTo("CANCELLED");
    }

    @Test
    void claimingAnObjectRunSupersedesQueuedRelationRunsForThatType() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_definition
                    (relation_id, owner_id, source_type_id, target_type_id, name, cardinality,
                     source_primary_key_attribute_id, source_primary_key_datatype,
                     target_primary_key_attribute_id, target_primary_key_datatype,
                     definition_revision)
                VALUES (?, ?, ?, ?, 'links', 'one_to_many', ?, 'text', ?, 'text', 1)
                """, relationId, ownerId, objectTypeId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        OntologyIndexRunEntity objectRun = new OntologyIndexRunEntity(
                UUID.randomUUID(), ownerId, SubjectKind.OBJECT_TYPE, objectTypeId,
                null, DatasetKind.SYNC, UUID.randomUUID(),
                json("{\"revision\":1}"), 1, TriggerType.MANUAL, null
        );
        OntologyIndexRunEntity relationRun = new OntologyIndexRunEntity(
                UUID.randomUUID(), ownerId, SubjectKind.RELATION, relationId,
                null, DatasetKind.SYNC, UUID.randomUUID(),
                json("{\"revision\":1}"), 1, TriggerType.MANUAL, null
        );
        UUID claimedId = runs.upsertQueued(objectRun).getId();
        UUID supersededId = runs.upsertQueued(relationRun).getId();

        assertThat(stateStore.claimNext("worker-a").orElseThrow().getId()).isEqualTo(claimedId);

        assertThat(runs.findById(supersededId).orElseThrow().getStatus())
                .isEqualTo(RunStatus.SUPERSEDED);
    }

    private UUID queue(SubjectKind kind, UUID subjectId) {
        OntologyIndexRunEntity run = new OntologyIndexRunEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                kind,
                subjectId,
                null,
                DatasetKind.SYNC,
                UUID.randomUUID(),
                json("{\"revision\":1}"),
                1,
                TriggerType.MANUAL,
                null
        );
        return runs.upsertQueued(run).getId();
    }
}
