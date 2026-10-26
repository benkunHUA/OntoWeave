package com.ontoweave.data.ontology.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.ontology.index.pipeline.ObjectBuildResult;
import com.ontoweave.data.ontology.index.pipeline.ObjectIndexPipeline;
import com.ontoweave.data.ontology.index.switching.IndexSwitcher;
import com.ontoweave.data.ontology.index.worker.OntologyIndexExecutor;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import com.ontoweave.data.ontology.store.ObjectStoreWriter;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class OntologyObjectPipelineIntegrationTest extends OntologyIndexIntegrationTestSupport {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NAME = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID AGE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String DATASET_SCHEMA = """
            {"fields":[
              {"name":"customer_id","type":"string","required":true},
              {"name":"name","type":"string","required":false},
              {"name":"age","type":"int","required":false}
            ]}
            """;

    @MockitoBean
    private IcebergSnapshotReader snapshots;

    @Autowired
    private ObjectIndexPipeline pipeline;

    @Autowired
    private IndexSwitcher switcher;

    @Autowired
    private OntologyIndexExecutor executor;

    @Autowired
    private OntologyIndexRunRepository runs;

    @Autowired
    private ObjectStoreWriter store;

    private final Schema icebergSchema = new Schema(
            Types.NestedField.required(1, "customer_id", Types.StringType.get()),
            Types.NestedField.optional(2, "name", Types.StringType.get()),
            Types.NestedField.optional(3, "age", Types.IntegerType.get())
    );

    @Test
    void stagesPromotesAndAtomicallySwitchesObjects() {
        Fixture fixture = fixture();
        when(snapshots.open(any(), anyLong())).thenReturn(read(
                record("c1", "Alice", 30),
                record("c2", "Bob", 41)
        ));
        OntologyIndexRunEntity run = insertRunningRun(fixture);

        ObjectBuildResult result = pipeline.execute(run, "worker-a");
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM data.ontology_object_version WHERE id = ?
                """, String.class, result.objectVersionId())).isEqualTo("BUILDING");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_instance WHERE version_id = ?
                """, Integer.class, result.objectVersionId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_value WHERE version_id = ?
                """, Integer.class, result.objectVersionId())).isEqualTo(6);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_stage WHERE run_id = ?
                """, Integer.class, run.getId())).isZero();

        switcher.switchObject(run, "worker-a", result);

        assertThat(jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_object_binding WHERE id = ?
                """, UUID.class, fixture.bindingId())).isEqualTo(result.objectVersionId());
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.outbox_event
                WHERE aggregate_id = ? AND event_type = 'ontology.index.run.succeeded'
                """, Integer.class, run.getId())).isEqualTo(1);
    }

    @Test
    void writesTimestampValuesIntoTimestampWithTimeZoneColumns() {
        UUID runId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-24T17:20:45Z");

        store.write(runId, List.of(new ObjectStoreWriter.ObjectStageRow(
                objectTypeId,
                "ts-1",
                null,
                null,
                occurredAt,
                "Timestamp row",
                null,
                List.of(new ObjectStoreWriter.ObjectValueStageRow(
                        attributeId, "ts-1", null, null, null, occurredAt, null))
        )));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_stage
                WHERE run_id = ? AND primary_key_ts IS NOT NULL
                """, Integer.class, runId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_value_stage
                WHERE run_id = ? AND ts_value IS NOT NULL
                """, Integer.class, runId)).isEqualTo(1);
        store.deleteStage(runId);
    }

    @Test
    void duplicatePrimaryKeysFailTheRunAndKeepTheOldVersionQueryable() {
        Fixture fixture = fixture();
        when(snapshots.open(any(), anyLong())).thenReturn(read(
                record("c1", "Alice", 30),
                record("c1", "Duplicate", 31)
        ));
        OntologyIndexRunEntity run = insertRunningRun(fixture);

        assertThatThrownBy(() -> pipeline.execute(run, "worker-a"))
                .isInstanceOf(IndexBuildException.class)
                .satisfies(error -> assertThat(((IndexBuildException) error).errorCode())
                        .isEqualTo(IndexFailureCode.STAGE_VALIDATION_FAILED));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_instance WHERE version_id = ?
                """, Integer.class, fixture.oldVersionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_object_binding WHERE id = ?
                """, UUID.class, fixture.bindingId())).isEqualTo(fixture.oldVersionId());
    }

    @Test
    void typeConversionFailureBlocksPromotion() {
        Fixture fixture = fixture();
        Schema invalidSchema = new Schema(
                Types.NestedField.required(1, "customer_id", Types.StringType.get()),
                Types.NestedField.optional(2, "name", Types.StringType.get()),
                Types.NestedField.optional(3, "age", Types.StringType.get())
        );
        GenericRecord invalid = GenericRecord.create(invalidSchema);
        invalid.setField("customer_id", "c1");
        invalid.setField("name", "Alice");
        invalid.setField("age", "not-a-number");
        when(snapshots.open(any(), anyLong())).thenReturn(new IcebergSnapshotReader.SnapshotRead(
                invalidSchema,
                CloseableIterable.withNoopClose(List.<Record>of(invalid))
        ));
        OntologyIndexRunEntity run = insertRunningRun(fixture);

        assertThatThrownBy(() -> pipeline.execute(run, "worker-a"))
                .isInstanceOf(IndexBuildException.class)
                .satisfies(error -> assertThat(((IndexBuildException) error).errorCode())
                        .isEqualTo(IndexFailureCode.STAGE_VALIDATION_FAILED));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_instance WHERE version_id <> ?
                """, Integer.class, fixture.oldVersionId())).isZero();
    }

    @Test
    void requiredAttributeMissingBlocksPromotion() {
        Fixture fixture = fixture();
        when(snapshots.open(any(), anyLong())).thenReturn(read(record("c1", null, 30)));
        OntologyIndexRunEntity run = insertRunningRun(fixture, mappingWithRequiredName());

        assertThatThrownBy(() -> pipeline.execute(run, "worker-a"))
                .isInstanceOf(IndexBuildException.class)
                .satisfies(error -> assertThat(((IndexBuildException) error).errorCode())
                        .isEqualTo(IndexFailureCode.STAGE_VALIDATION_FAILED));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_instance WHERE version_id <> ?
                """, Integer.class, fixture.oldVersionId())).isZero();
    }

    @Test
    void leaseLossPreventsSwitchingTheObjectPointer() {
        Fixture fixture = fixture();
        when(snapshots.open(any(), anyLong())).thenReturn(read(record("c1", "Alice", 30)));
        OntologyIndexRunEntity run = insertRunningRun(fixture);
        ObjectBuildResult result = pipeline.execute(run, "worker-a");

        assertThatThrownBy(() -> switcher.switchObject(run, "worker-b", result))
                .isInstanceOf(IndexBuildException.class)
                .satisfies(error -> assertThat(((IndexBuildException) error).errorCode())
                        .isEqualTo(IndexFailureCode.LEASE_LOST));
        assertThat(jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_object_binding WHERE id = ?
                """, UUID.class, fixture.bindingId())).isEqualTo(fixture.oldVersionId());
        assertThat(jdbc.queryForObject("""
                SELECT status FROM data.ontology_object_version WHERE id = ?
                """, String.class, result.objectVersionId())).isEqualTo("BUILDING");
    }

    @Test
    void runningCancellationStopsBeforePromotionAndCleansArtifacts() {
        Fixture fixture = fixture();
        when(snapshots.open(any(), anyLong())).thenReturn(read());
        OntologyIndexRunEntity run = insertRunningRun(fixture);
        jdbc.update("""
                UPDATE data.ontology_index_run SET cancel_requested_at = now() WHERE id = ?
                """, run.getId());

        executor.execute(run, "worker-a");

        assertThat(runs.findById(run.getId()).orElseThrow().getStatus())
                .isEqualTo(RunStatus.CANCELLED);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_version WHERE run_id = ?
                """, Integer.class, run.getId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.outbox_event
                WHERE aggregate_id = ? AND event_type = 'ontology.index.run.cancelled'
                """, Integer.class, run.getId())).isEqualTo(1);
    }

    private Fixture fixture() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        SyncDataset dataset = syncDataset(ownerId, DATASET_SCHEMA, 1L);
        UUID oldVersionId = UUID.randomUUID();
        insertObjectBinding(bindingId, ownerId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), null, "OK");
        insertObjectVersion(oldVersionId, ownerId, bindingId, objectTypeId, dataset, mapping(), "ACTIVE");
        jdbc.update("UPDATE data.ontology_object_binding SET current_index_version_id = ? WHERE id = ?",
                oldVersionId, bindingId);
        jdbc.update("""
                INSERT INTO data.ontology_object_instance
                    (version_id, object_type_id, primary_key_value, title_value, properties)
                VALUES (?, ?, 'old', 'Old', '{}'::jsonb)
                """, oldVersionId, objectTypeId);
        return new Fixture(ownerId, objectTypeId, bindingId, dataset, oldVersionId);
    }

    private OntologyIndexRunEntity insertRunningRun(Fixture fixture) {
        return insertRunningRun(fixture, mapping());
    }

    private OntologyIndexRunEntity insertRunningRun(Fixture fixture, JsonNode mappingSnapshot) {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id, target_dataset_kind,
                     target_dataset_version_id, mapping_snapshot, mapping_revision,
                     trigger_type, status, phase, started_at, heartbeat_at, lease_owner,
                     lease_expires_at, attempt)
                VALUES (?, ?, 'OBJECT_TYPE', ?, ?, 'SYNC', ?, CAST(? AS jsonb), 1,
                        'MANUAL', 'RUNNING', 'READING', now(), now(), 'worker-a',
                        now() + interval '10 minutes', 1)
                """, runId, fixture.ownerId(), fixture.objectTypeId(), fixture.bindingId(),
                fixture.dataset().versionId(), mappingSnapshot.toString());
        return runs.findById(runId).orElseThrow();
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
                     "column":"name","datatype":"text","required":false,"isArray":false},
                    {"attributeId":"33333333-3333-3333-3333-333333333333",
                     "column":"age","datatype":"integer","required":false,"isArray":false}
                  ],
                  "revision":1
                }
                """);
    }

    private JsonNode mappingWithRequiredName() {
        return json("""
                {
                  "primaryKeyAttributeId":"11111111-1111-1111-1111-111111111111",
                  "titleKeyAttributeId":"22222222-2222-2222-2222-222222222222",
                  "attributes":[
                    {"attributeId":"11111111-1111-1111-1111-111111111111",
                     "column":"customer_id","datatype":"text","required":true,"isArray":false},
                    {"attributeId":"22222222-2222-2222-2222-222222222222",
                     "column":"name","datatype":"text","required":true,"isArray":false},
                    {"attributeId":"33333333-3333-3333-3333-333333333333",
                     "column":"age","datatype":"integer","required":false,"isArray":false}
                  ],
                  "revision":1
                }
                """);
    }

    private GenericRecord record(String id, String name, Integer age) {
        GenericRecord record = GenericRecord.create(icebergSchema);
        record.setField("customer_id", id);
        record.setField("name", name);
        record.setField("age", age);
        return record;
    }

    private IcebergSnapshotReader.SnapshotRead read(GenericRecord... records) {
        return new IcebergSnapshotReader.SnapshotRead(
                icebergSchema,
                CloseableIterable.withNoopClose(List.<Record>of(records))
        );
    }

    private record Fixture(
            UUID ownerId,
            UUID objectTypeId,
            UUID bindingId,
            SyncDataset dataset,
            UUID oldVersionId
    ) {
    }
}
