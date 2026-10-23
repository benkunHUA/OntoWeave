package com.ontoweave.data.ontology.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.index.queue.OntologyIndexEnqueuer;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import com.ontoweave.data.ontology.store.IndexEnqueuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyIndexEnqueuerIntegrationTest extends OntologyIndexIntegrationTestSupport {

    private static final String SCHEMA = """
            {"fields":[
              {"name":"customer_id","type":"string","required":true},
              {"name":"name","type":"string","required":false}
            ]}
            """;

    @Autowired
    private OntologyIndexEnqueuer enqueuer;

    @Autowired
    private OntologyIndexRunRepository runs;

    @Test
    void publishHitMergesQueuedRunAndUpdatesTheTargetVersion() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        SyncDataset dataset = syncDataset(ownerId, SCHEMA, 1L);
        UUID firstVersion = UUID.randomUUID();
        UUID secondVersion = addSyncDatasetVersion(dataset, SCHEMA, 2L);
        JsonNode mapping = mapping();
        insertObjectBinding(bindingId, ownerId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), null, "OK");
        insertObjectVersion(firstVersion, ownerId, bindingId, objectTypeId, dataset, mapping, "ACTIVE");
        jdbc.update("UPDATE data.ontology_object_binding SET current_index_version_id = ? WHERE id = ?",
                firstVersion, bindingId);

        UUID first = enqueuer.enqueueOnPublish(DatasetKind.SYNC, dataset.datasetId(), dataset.versionId());
        UUID second = enqueuer.enqueueOnPublish(DatasetKind.SYNC, dataset.datasetId(), secondVersion);

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_index_run WHERE status = 'QUEUED'
                """, Integer.class)).isEqualTo(1);
        OntologyIndexRunEntity run = runs.findById(first).orElseThrow();
        assertThat(run.getTargetDatasetVersionId()).isEqualTo(secondVersion);
        assertThat(run.getTriggerType()).isEqualTo(TriggerType.DATASET_PUBLISHED);
    }

    @Test
    void invalidPublishedSchemaMarksTheBindingForRepairWithoutQueueing() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        SyncDataset dataset = syncDataset(ownerId, SCHEMA, 1L);
        UUID currentVersion = UUID.randomUUID();
        JsonNode mapping = mapping();
        insertObjectBinding(bindingId, ownerId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), null, "OK");
        insertObjectVersion(currentVersion, ownerId, bindingId, objectTypeId, dataset, mapping, "ACTIVE");
        jdbc.update("UPDATE data.ontology_object_binding SET current_index_version_id = ? WHERE id = ?",
                currentVersion, bindingId);
        UUID invalidVersion = addSyncDatasetVersion(dataset, """
                {"fields":[{"name":"customer_id","type":"string","required":true}]}
                """, 2L);

        UUID queued = enqueuer.enqueueOnPublish(DatasetKind.SYNC, dataset.datasetId(), invalidVersion);

        assertThat(queued).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT validation_state FROM data.ontology_object_binding WHERE id = ?
                """, String.class, bindingId)).isEqualTo("NEEDS_REPAIR");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_index_run WHERE status = 'QUEUED'
                """, Integer.class)).isZero();
    }

    @Test
    void retryCopiesTheFailedRunAndIncrementsAttempt() {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, target_dataset_kind,
                     target_dataset_version_id, mapping_snapshot, mapping_revision,
                     trigger_type, status, attempt, finished_at)
                VALUES (?, ?, 'OBJECT_TYPE', ?, 'SYNC', ?, CAST(? AS jsonb), 1, 'MANUAL',
                        'FAILED', 3, now())
                """, runId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                mapping().toString());

        UUID retryId = enqueuer.enqueueRetry(runId);

        OntologyIndexRunEntity retry = runs.findById(retryId).orElseThrow();
        assertThat(retry.getTriggerType()).isEqualTo(TriggerType.RETRY);
        assertThat(retry.getAttempt()).isEqualTo(4);
        assertThat(retry.getParentRunId()).isEqualTo(runId);
    }

    @Test
    void manualObjectEnqueueValidatesBindingAndDatasetVersion() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        SyncDataset dataset = syncDataset(ownerId, SCHEMA, 1L);
        insertObjectBinding(bindingId, ownerId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), null, "OK");
        MappingSnapshot snapshot = MappingSnapshot.object(
                UUID.fromString(mapping().path("primaryKeyAttributeId").asText()),
                UUID.fromString(mapping().path("titleKeyAttributeId").asText()),
                List.of(
                        new MappingSnapshot.AttributeMapping(
                                UUID.fromString(mapping().path("attributes").get(0).path("attributeId").asText()),
                                "customer_id", "text", true, false, null),
                        new MappingSnapshot.AttributeMapping(
                                UUID.fromString(mapping().path("attributes").get(1).path("attributeId").asText()),
                                "name", "text", false, false, null)
                ),
                1
        );

        UUID runId = enqueuer.enqueueManual(new IndexEnqueuer.ManualIndexRequest(
                ownerId, SubjectKind.OBJECT_TYPE, objectTypeId, bindingId,
                DatasetKind.SYNC, dataset.versionId(), snapshot, 1
        ));

        assertThat(runs.findById(runId)).isPresent();
    }

    private JsonNode mapping() {
        return json("""
                {
                  "primaryKeyAttributeId":"11111111-1111-1111-1111-111111111111",
                  "titleKeyAttributeId":"22222222-2222-2222-2222-222222222222",
                  "attributes":[
                    {"attributeId":"11111111-1111-1111-1111-111111111111",
                     "column":"customer_id","datatype":"text","required":true,"isArray":false},
                    {"attributeId":"22222222-2222-2222-2222-222222222222",
                     "column":"name","datatype":"text","required":false,"isArray":false}
                  ],
                  "revision":1
                }
                """);
    }
}
