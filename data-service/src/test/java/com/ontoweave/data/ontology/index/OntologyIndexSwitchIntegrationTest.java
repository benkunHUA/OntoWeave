package com.ontoweave.data.ontology.index;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.index.pipeline.ObjectBuildResult;
import com.ontoweave.data.ontology.index.pipeline.RelationBuildResult;
import com.ontoweave.data.ontology.index.switching.IndexSwitcher;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OntologyIndexSwitchIntegrationTest extends OntologyIndexIntegrationTestSupport {

    @Autowired
    private IndexSwitcher switcher;

    @Autowired
    private OntologyIndexRunRepository runs;

    @Test
    void failedRelationActivationRollsBackTheWholeObjectSwitch() {
        Fixture fixture = fixture();
        UUID relationBindingId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID relationVersion = UUID.randomUUID();
        insertRelationBinding(relationBindingId, fixture.ownerId(), relationId,
                fixture.dataset(), null);
        insertRelationVersion(relationVersion, fixture.ownerId(), relationBindingId, relationId,
                fixture.objectTypeId(), fixture.run().getId(), fixture.newVersionId(), "ACTIVE");
        ObjectBuildResult result = new ObjectBuildResult(
                fixture.newVersionId(), 1, 0,
                List.of(new RelationBuildResult(relationVersion, relationId, 1, 0)),
                Map.of("rows", 1)
        );

        assertThatThrownBy(() -> switcher.switchObject(fixture.run(), "worker-a", result))
                .isInstanceOf(IndexBuildException.class);

        assertThat(objectStatus(fixture.oldVersionId())).isEqualTo("ACTIVE");
        assertThat(objectStatus(fixture.newVersionId())).isEqualTo("BUILDING");
        assertThat(currentObjectVersion(fixture.bindingId())).isEqualTo(fixture.oldVersionId());
        assertThat(runs.findById(fixture.run().getId()).orElseThrow().getStatus())
                .isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void staleRelationVersionsAreDiscardedAndTheirInstancesRemoved() {
        Fixture fixture = fixture();
        UUID relationId = UUID.randomUUID();
        UUID relationVersion = UUID.randomUUID();
        insertRelationVersion(relationVersion, fixture.ownerId(), null, relationId,
                fixture.objectTypeId(), fixture.run().getId(), UUID.randomUUID(), "BUILDING");
        jdbc.update("""
                INSERT INTO data.ontology_relation_instance
                    (version_id, relation_id, from_object_type_id, from_primary_key,
                     to_object_type_id, to_primary_key)
                VALUES (?, ?, ?, 'old-source', ?, 'old-target')
                """, relationVersion, relationId, fixture.objectTypeId(), fixture.objectTypeId());
        ObjectBuildResult result = new ObjectBuildResult(
                fixture.newVersionId(), 1, 0,
                List.of(new RelationBuildResult(relationVersion, relationId, 1, 0)),
                Map.of("rows", 1)
        );

        switcher.switchObject(fixture.run(), "worker-a", result);

        assertThat(relationStatus(relationVersion)).isEqualTo("DISCARDED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_relation_instance WHERE version_id = ?
                """, Integer.class, relationVersion)).isZero();
        assertThat(currentObjectVersion(fixture.bindingId())).isEqualTo(fixture.newVersionId());
    }

    private Fixture fixture() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        UUID oldVersionId = UUID.randomUUID();
        UUID newVersionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        SyncDataset dataset = syncDataset(ownerId, "{\"fields\":[]}", 1L);
        insertObjectBinding(bindingId, ownerId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), oldVersionId, "OK");
        insertObjectVersion(oldVersionId, ownerId, bindingId, objectTypeId, dataset,
                json("{\"attributes\":[]}"), "ACTIVE");
        jdbc.update("""
                INSERT INTO data.ontology_object_version
                    (id, owner_id, binding_id, object_type_id, source_dataset_kind,
                     source_dataset_id, source_dataset_version_id, source_snapshot_id,
                     mapping_snapshot, mapping_revision, run_id, status, row_count)
                VALUES (?, ?, ?, ?, 'SYNC', ?, ?, 1, '{"attributes":[]}'::jsonb, 1, ?,
                        'BUILDING', 1)
                """, newVersionId, ownerId, bindingId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), runId);
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id, target_dataset_kind,
                     target_dataset_version_id, mapping_snapshot, mapping_revision,
                     trigger_type, status, phase, started_at, heartbeat_at, lease_owner,
                     lease_expires_at, attempt)
                VALUES (?, ?, 'OBJECT_TYPE', ?, ?, 'SYNC', ?, '{"attributes":[]}'::jsonb, 1,
                        'MANUAL', 'RUNNING', 'WRITING', now(), now(), 'worker-a',
                        now() + interval '10 minutes', 1)
                """, runId, ownerId, objectTypeId, bindingId, dataset.versionId());
        OntologyIndexRunEntity run = runs.findById(runId).orElseThrow();
        return new Fixture(ownerId, objectTypeId, bindingId, dataset, oldVersionId, newVersionId, run);
    }

    private void insertRelationVersion(
            UUID id,
            UUID ownerId,
            UUID bindingId,
            UUID relationId,
            UUID objectTypeId,
            UUID runId,
            UUID objectVersionId,
            String status
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_relation_version
                    (id, owner_id, binding_id, relation_id, relation_kind,
                     from_object_type_id, to_object_type_id, from_object_version_id,
                     to_object_version_id, source_dataset_kind, source_dataset_id,
                     source_dataset_version_id, source_snapshot_id, mapping_snapshot,
                     mapping_revision, run_id, status, row_count, skipped_count)
                VALUES (?, ?, ?, ?, 'FK', ?, ?, ?, ?, 'SYNC', ?, ?, 1, '{}'::jsonb, 1, ?,
                        ?, 1, 0)
                """, id, ownerId, bindingId, relationId, objectTypeId, objectTypeId,
                objectVersionId, objectVersionId, UUID.randomUUID(), UUID.randomUUID(),
                runId, status);
    }

    private String objectStatus(UUID versionId) {
        return jdbc.queryForObject(
                "SELECT status FROM data.ontology_object_version WHERE id = ?",
                String.class,
                versionId
        );
    }

    private String relationStatus(UUID versionId) {
        return jdbc.queryForObject(
                "SELECT status FROM data.ontology_relation_version WHERE id = ?",
                String.class,
                versionId
        );
    }

    private UUID currentObjectVersion(UUID bindingId) {
        return jdbc.queryForObject(
                "SELECT current_index_version_id FROM data.ontology_object_binding WHERE id = ?",
                UUID.class,
                bindingId
        );
    }

    private record Fixture(
            UUID ownerId,
            UUID objectTypeId,
            UUID bindingId,
            SyncDataset dataset,
            UUID oldVersionId,
            UUID newVersionId,
            OntologyIndexRunEntity run
    ) {
    }
}
