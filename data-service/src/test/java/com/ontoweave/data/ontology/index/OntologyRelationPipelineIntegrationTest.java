package com.ontoweave.data.ontology.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.ontology.index.pipeline.EndpointVersion;
import com.ontoweave.data.ontology.index.pipeline.ForeignKeyRelationBuilder;
import com.ontoweave.data.ontology.index.pipeline.RelationBuildResult;
import com.ontoweave.data.ontology.index.pipeline.RelationRunPipeline;
import com.ontoweave.data.ontology.index.pipeline.RelationRunResult;
import com.ontoweave.data.ontology.index.switching.IndexSwitcher;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class OntologyRelationPipelineIntegrationTest extends OntologyIndexIntegrationTestSupport {

    private static final Schema CONNECTION_SCHEMA = new Schema(
            Types.NestedField.required(1, "from_id", Types.StringType.get()),
            Types.NestedField.required(2, "to_id", Types.StringType.get())
    );

    @MockitoBean
    private IcebergSnapshotReader snapshots;

    @Autowired
    private RelationRunPipeline relationPipeline;

    @Autowired
    private ForeignKeyRelationBuilder foreignKeyBuilder;

    @Autowired
    private IndexSwitcher switcher;

    @Autowired
    private OntologyIndexRunRepository runs;

    @Test
    void generatesManyToManyRelationsAndSkipsMissingAndDuplicatePairs() {
        M2MFixture fixture = m2mFixture(true);
        when(snapshots.open(any(), anyLong())).thenReturn(read(
                pair("c1", "o1"),
                pair("c1", "o1"),
                pair("c1", "missing"),
                pair("missing", "o1"),
                pair("c2", "o2")
        ));
        OntologyIndexRunEntity run = insertRunningRelationRun(fixture);

        RelationRunResult result = relationPipeline.execute(run, () -> { });

        assertThat(result.relation()).isNotNull();
        assertThat(result.relation().rowCount()).isEqualTo(2);
        assertThat(result.relation().skippedCount()).isEqualTo(3);
        switcher.switchRelation(run, "worker-a", result);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_relation_instance WHERE version_id = ?
                """, Integer.class, result.relation().relationVersionId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_relation_binding WHERE id = ?
                """, UUID.class, fixture.bindingId()))
                .isEqualTo(result.relation().relationVersionId());
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(RunStatus.SUCCEEDED);
    }

    @Test
    void marksTheRunSupersededWhenTheConnectionDatasetChangedBeforeSwitch() {
        M2MFixture fixture = m2mFixture(false);
        when(snapshots.open(any(), anyLong())).thenReturn(read(pair("c1", "o1")));
        OntologyIndexRunEntity run = insertRunningRelationRun(fixture);
        RelationRunResult result = relationPipeline.execute(run, () -> { });

        switcher.switchRelation(run, "worker-a", result);

        assertThat(runs.findById(run.getId()).orElseThrow().getStatus())
                .isEqualTo(RunStatus.SUPERSEDED);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM data.ontology_relation_version WHERE id = ?
                """, String.class, result.relation().relationVersionId())).isEqualTo("DISCARDED");
        assertThat(jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_relation_binding WHERE id = ?
                """, UUID.class, fixture.bindingId())).isNull();
    }

    @Test
    void firstManyToManyBuildSucceedsBeforeDatasetVersionIsActivated() {
        M2MFixture fixture = m2mFixture(true);
        jdbc.update("""
                UPDATE data.ontology_relation_binding
                SET active_dataset_version_id = NULL WHERE id = ?
                """, fixture.bindingId());
        when(snapshots.open(any(), anyLong())).thenReturn(read(pair("c1", "o1")));
        OntologyIndexRunEntity run = insertRunningRelationRun(fixture);

        RelationRunResult result = relationPipeline.execute(run, () -> { });
        switcher.switchRelation(run, "worker-a", result);

        assertThat(runs.findById(run.getId()).orElseThrow().getStatus())
                .isEqualTo(RunStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_relation_binding WHERE id = ?
                """, UUID.class, fixture.bindingId()))
                .isEqualTo(result.relation().relationVersionId());
    }

    @Test
    void buildsForeignKeyRelationsAgainstTheTargetVersionAndSkipsMissingTargets() {
        UUID ownerId = UUID.randomUUID();
        UUID sourceType = UUID.randomUUID();
        UUID targetType = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID foreignKeyAttribute = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        UUID targetVersion = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_object_instance
                    (version_id, object_type_id, primary_key_value, title_value, properties)
                VALUES (?, ?, 's1', 'S1', '{}'::jsonb), (?, ?, 's2', 'S2', '{}'::jsonb)
                """, sourceVersion, sourceType, sourceVersion, sourceType);
        jdbc.update("""
                INSERT INTO data.ontology_object_instance
                    (version_id, object_type_id, primary_key_value, title_value, properties)
                VALUES (?, ?, 't1', 'T1', '{}'::jsonb)
                """, targetVersion, targetType);
        jdbc.update("""
                INSERT INTO data.ontology_object_value
                    (version_id, attribute_id, primary_key_value, text_value)
                VALUES (?, ?, 's1', 't1'), (?, ?, 's2', 'missing')
                """, sourceVersion, foreignKeyAttribute, sourceVersion, foreignKeyAttribute);
        OntologyRelationDefinitionEntity definition = new OntologyRelationDefinitionEntity(
                relationId, ownerId, sourceType, targetType, "owns", "one_to_many",
                foreignKeyAttribute, "text", UUID.randomUUID(), "text",
                UUID.randomUUID(), "text", 1
        );
        MappingSnapshot sourceMapping = MappingSnapshot.object(
                UUID.randomUUID(), null,
                List.of(new MappingSnapshot.AttributeMapping(
                        foreignKeyAttribute, "target_id", "text", false, false, null)),
                1
        );
        EndpointVersion from = new EndpointVersion(
                sourceType, sourceVersion, sourceMapping, mapper.valueToTree(sourceMapping), 1,
                DatasetKind.SYNC, UUID.randomUUID(), UUID.randomUUID(), 1L
        );
        EndpointVersion to = new EndpointVersion(
                targetType, targetVersion, MappingSnapshot.object(null, null, List.of(), 1),
                json("{\"attributes\":[]}"), 1,
                DatasetKind.SYNC, UUID.randomUUID(), UUID.randomUUID(), 1L
        );

        RelationBuildResult result = foreignKeyBuilder.build(runId, ownerId, definition, from, to);

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_relation_instance
                WHERE version_id = ? AND from_primary_key = 's1' AND to_primary_key = 't1'
                """, Integer.class, result.relationVersionId())).isEqualTo(1);
    }

    private M2MFixture m2mFixture(boolean currentSourceDataset) {
        UUID ownerId = UUID.randomUUID();
        UUID sourceType = UUID.randomUUID();
        UUID targetType = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        UUID targetVersion = UUID.randomUUID();
        SyncDataset objects = syncDataset(ownerId, "{\"fields\":[]}", 10L);
        SyncDataset connections = syncDataset(ownerId, """
                {"fields":[
                  {"name":"from_id","type":"string","required":true},
                  {"name":"to_id","type":"string","required":true}
                ]}
                """, 20L);
        jdbc.update("""
                INSERT INTO data.ontology_relation_definition
                    (relation_id, owner_id, source_type_id, target_type_id, name, cardinality,
                     source_primary_key_attribute_id, source_primary_key_datatype,
                     target_primary_key_attribute_id, target_primary_key_datatype,
                     definition_revision)
                VALUES (?, ?, ?, ?, 'links', 'many_to_many', ?, 'text', ?, 'text', 1)
                """, relationId, ownerId, sourceType, targetType,
                UUID.randomUUID(), UUID.randomUUID());
        UUID sourceBinding = UUID.randomUUID();
        UUID targetBinding = UUID.randomUUID();
        insertObjectBinding(sourceBinding, ownerId, sourceType, objects.datasetId(),
                objects.versionId(), null, "OK");
        insertObjectVersion(sourceVersion, ownerId, sourceBinding, sourceType, objects,
                json("{\"attributes\":[]}"), "ACTIVE");
        jdbc.update("UPDATE data.ontology_object_binding SET current_index_version_id = ? WHERE id = ?",
                sourceVersion, sourceBinding);
        insertObjectBinding(targetBinding, ownerId, targetType, objects.datasetId(),
                objects.versionId(), null, "OK");
        insertObjectVersion(targetVersion, ownerId, targetBinding, targetType, objects,
                json("{\"attributes\":[]}"), "ACTIVE");
        jdbc.update("UPDATE data.ontology_object_binding SET current_index_version_id = ? WHERE id = ?",
                targetVersion, targetBinding);
        jdbc.update("""
                INSERT INTO data.ontology_object_instance
                    (version_id, object_type_id, primary_key_value, title_value, properties)
                VALUES (?, ?, 'c1', 'C1', '{}'::jsonb), (?, ?, 'c2', 'C2', '{}'::jsonb)
                """, sourceVersion, sourceType, sourceVersion, sourceType);
        jdbc.update("""
                INSERT INTO data.ontology_object_instance
                    (version_id, object_type_id, primary_key_value, title_value, properties)
                VALUES (?, ?, 'o1', 'O1', '{}'::jsonb), (?, ?, 'o2', 'O2', '{}'::jsonb)
                """, targetVersion, targetType, targetVersion, targetType);
        insertRelationBinding(bindingId, ownerId, relationId, connections, null);
        if (!currentSourceDataset) {
            UUID stale = addSyncDatasetVersion(connections, "{\"fields\":[]}", 21L);
            jdbc.update("""
                    UPDATE data.ontology_relation_binding SET active_dataset_version_id = ?
                    WHERE id = ?
                    """, stale, bindingId);
        }
        return new M2MFixture(ownerId, relationId, bindingId, connections);
    }

    private OntologyIndexRunEntity insertRunningRelationRun(M2MFixture fixture) {
        UUID runId = UUID.randomUUID();
        JsonNode mapping = json("""
                {"fromKeyColumn":"from_id","fromKeyDatatype":"text",
                 "toKeyColumn":"to_id","toKeyDatatype":"text","revision":1}
                """);
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id, target_dataset_kind,
                     target_dataset_version_id, mapping_snapshot, mapping_revision,
                     trigger_type, status, phase, started_at, heartbeat_at, lease_owner,
                     lease_expires_at, attempt)
                VALUES (?, ?, 'RELATION', ?, ?, 'SYNC', ?, CAST(? AS jsonb), 1,
                        'MANUAL', 'RUNNING', 'READING', now(), now(), 'worker-a',
                        now() + interval '10 minutes', 1)
                """, runId, fixture.ownerId(), fixture.relationId(), fixture.bindingId(),
                fixture.dataset().versionId(), mapping.toString());
        return runs.findById(runId).orElseThrow();
    }

    private GenericRecord pair(String from, String to) {
        GenericRecord record = GenericRecord.create(CONNECTION_SCHEMA);
        record.setField("from_id", from);
        record.setField("to_id", to);
        return record;
    }

    private IcebergSnapshotReader.SnapshotRead read(GenericRecord... records) {
        return new IcebergSnapshotReader.SnapshotRead(
                CONNECTION_SCHEMA,
                CloseableIterable.withNoopClose(List.<Record>of(records))
        );
    }

    private record M2MFixture(
            UUID ownerId,
            UUID relationId,
            UUID bindingId,
            SyncDataset dataset
    ) {
    }
}
