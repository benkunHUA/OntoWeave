package com.ontoweave.data.ontology.foundation;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.common.ValidationState;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import com.ontoweave.data.ontology.repository.OntologyObjectBindingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyRepositoryIntegrationTest extends OntologyFoundationIntegrationTestSupport {

    @Autowired
    private OntologyIndexRunRepository runs;

    @Autowired
    private OntologyObjectBindingRepository bindings;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void upsertQueuedMergesIntoTheExistingQueuedRun() {
        UUID subjectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID firstVersion = UUID.randomUUID();
        UUID secondVersion = UUID.randomUUID();

        OntologyIndexRunEntity first = runs.upsertQueued(
                queuedRun(ownerId, subjectId, firstVersion, "{\"revision\":1}", 1)
        );
        OntologyIndexRunEntity merged = runs.upsertQueued(
                queuedRun(ownerId, subjectId, secondVersion, "{\"revision\":2}", 2)
        );

        assertThat(merged.getId()).isEqualTo(first.getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_index_run WHERE status = 'QUEUED'",
                Integer.class
        )).isEqualTo(1);
        assertThat(merged.getTargetDatasetVersionId()).isEqualTo(secondVersion);
        assertThat(merged.getMappingRevision()).isEqualTo(2L);
        assertThat(merged.getStatus()).isEqualTo(RunStatus.QUEUED);
    }

    @Test
    void lockNextQueuedGivesConcurrentWorkersDifferentRuns() throws Exception {
        runs.upsertQueued(queuedRun(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "{\"revision\":1}", 1
        ));
        runs.upsertQueued(queuedRun(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "{\"revision\":2}", 2
        ));
        CountDownLatch claimed = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = pool.submit(() -> claimAndHold(claimed, release));
            Future<UUID> second = pool.submit(() -> claimAndHold(claimed, release));
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            List<UUID> ids = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertThat(ids).doesNotHaveDuplicates();
            assertThat(ids).allMatch(id -> id != null);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void updateDraftUsesRevisionCompareAndSet() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        UUID firstDataset = UUID.randomUUID();
        UUID secondDataset = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_object_binding
                    (id, owner_id, object_type_id, draft_dataset_kind, draft_dataset_id,
                     draft_mapping)
                VALUES (?, ?, ?, 'SYNC', ?, '{}'::jsonb)
                """, bindingId, ownerId, objectTypeId, firstDataset);

        int stale = bindings.updateDraft(
                bindingId, 0, DatasetKind.PIPELINE, secondDataset,
                "{\"revision\":2}", ValidationState.OK, null
        );
        int current = bindings.updateDraft(
                bindingId, 1, DatasetKind.PIPELINE, secondDataset,
                "{\"revision\":2}", ValidationState.OK, null
        );
        int duplicate = bindings.updateDraft(
                bindingId, 1, DatasetKind.SYNC, firstDataset,
                "{\"revision\":3}", ValidationState.OK, null
        );

        assertThat(stale).isZero();
        assertThat(current).isEqualTo(1);
        assertThat(duplicate).isZero();
        OntologyObjectBindingEntity saved = bindings.findById(bindingId).orElseThrow();
        assertThat(saved.getRevision()).isEqualTo(2L);
        assertThat(saved.getDraftDatasetKind()).isEqualTo(DatasetKind.PIPELINE);
        assertThat(saved.getDraftDatasetId()).isEqualTo(secondDataset);
    }

    @Test
    void publishHitQueryOnlyReturnsRepairableFreeActiveBindings() {
        UUID ownerId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        UUID repairing = UUID.randomUUID();
        insertBinding(healthy, ownerId, datasetId, "OK");
        insertBinding(repairing, ownerId, datasetId, "NEEDS_REPAIR");

        assertThat(bindings.findByActiveDatasetKindAndActiveDatasetIdAndValidationState(
                DatasetKind.PIPELINE, datasetId, ValidationState.OK
        )).extracting(OntologyObjectBindingEntity::getId).containsExactly(healthy);
    }

    private UUID claimAndHold(CountDownLatch claimed, CountDownLatch release) {
        return new TransactionTemplate(transactions).execute(status -> {
            OntologyIndexRunEntity run = runs.lockNextQueued().orElse(null);
            claimed.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return run == null ? null : run.getId();
        });
    }

    private OntologyIndexRunEntity queuedRun(
            UUID ownerId,
            UUID subjectId,
            UUID targetVersionId,
            String mapping,
            long mappingRevision
    ) {
        return new OntologyIndexRunEntity(
                UUID.randomUUID(),
                ownerId,
                SubjectKind.OBJECT_TYPE,
                subjectId,
                null,
                DatasetKind.PIPELINE,
                targetVersionId,
                json(mapping),
                mappingRevision,
                TriggerType.MANUAL,
                null
        );
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void insertBinding(UUID id, UUID ownerId, UUID datasetId, String state) {
        jdbc.update("""
                INSERT INTO data.ontology_object_binding
                    (id, owner_id, object_type_id, draft_dataset_kind, draft_dataset_id,
                     draft_mapping, active_dataset_kind, active_dataset_id,
                     validation_state)
                VALUES (?, ?, ?, 'PIPELINE', ?, '{}'::jsonb, 'PIPELINE', ?, ?)
                """, id, ownerId, UUID.randomUUID(), datasetId, datasetId, state);
    }
}
