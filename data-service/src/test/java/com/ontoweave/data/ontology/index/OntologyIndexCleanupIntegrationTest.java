package com.ontoweave.data.ontology.index;

import com.ontoweave.data.ontology.index.cleanup.VersionCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "ontoweave.ontology.index.cleanup-batch-size=2")
class OntologyIndexCleanupIntegrationTest extends OntologyIndexIntegrationTestSupport {

    @Autowired
    private VersionCleanupService cleanup;

    @Test
    void deletesOnlyExpiredRetiredVersionsInBatches() {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        SyncDataset dataset = syncDataset(ownerId, "{\"fields\":[]}", 1L);
        insertObjectBinding(bindingId, ownerId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), null, "OK");

        UUID expiredObject = UUID.randomUUID();
        UUID recentObject = UUID.randomUUID();
        UUID expiredRelation = UUID.randomUUID();
        insertRetiredObjectVersion(expiredObject, ownerId, bindingId, objectTypeId, dataset,
                Instant.now().minusSeconds(600));
        insertRetiredObjectVersion(recentObject, ownerId, bindingId, objectTypeId, dataset,
                Instant.now().minusSeconds(60));
        insertRetiredRelationVersion(expiredRelation, ownerId, dataset);

        for (int index = 0; index < 5; index++) {
            jdbc.update("""
                    INSERT INTO data.ontology_object_instance
                        (version_id, object_type_id, primary_key_value, title_value, properties)
                    VALUES (?, ?, ?, ?, '{}'::jsonb)
                    """, expiredObject, objectTypeId, "key-" + index, "title-" + index);
            jdbc.update("""
                    INSERT INTO data.ontology_object_value
                        (version_id, attribute_id, primary_key_value, text_value)
                    VALUES (?, ?, ?, ?)
                    """, expiredObject, UUID.randomUUID(), "key-" + index, "value-" + index);
            jdbc.update("""
                    INSERT INTO data.ontology_relation_instance
                        (version_id, relation_id, from_object_type_id, from_primary_key,
                         to_object_type_id, to_primary_key)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, expiredRelation, UUID.randomUUID(), objectTypeId, "from-" + index,
                    UUID.randomUUID(), "to-" + index);
        }
        jdbc.update("""
                INSERT INTO data.ontology_object_instance
                    (version_id, object_type_id, primary_key_value, title_value, properties)
                VALUES (?, ?, 'recent', 'Recent', '{}'::jsonb)
                """, recentObject, objectTypeId);

        VersionCleanupService.CleanupResult result = cleanup.runOnce(Instant.now());

        assertThat(result.objectVersions()).isEqualTo(1);
        assertThat(result.relationVersions()).isEqualTo(1);
        assertThat(count("data.ontology_object_version", expiredObject)).isZero();
        assertThat(count("data.ontology_object_instance", expiredObject)).isZero();
        assertThat(count("data.ontology_object_value", expiredObject)).isZero();
        assertThat(count("data.ontology_relation_version", expiredRelation)).isZero();
        assertThat(count("data.ontology_relation_instance", expiredRelation)).isZero();
        assertThat(count("data.ontology_object_version", recentObject)).isEqualTo(1);
        assertThat(count("data.ontology_object_instance", recentObject)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT cleanup_attempts FROM data.ontology_object_version WHERE id = ?
                """, Integer.class, recentObject)).isZero();
    }

    private void insertRetiredObjectVersion(
            UUID versionId,
            UUID ownerId,
            UUID bindingId,
            UUID objectTypeId,
            SyncDataset dataset,
            Instant retiredAt
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_object_version
                    (id, owner_id, binding_id, object_type_id, source_dataset_kind,
                     source_dataset_id, source_dataset_version_id, source_snapshot_id,
                     mapping_snapshot, mapping_revision, run_id, status, row_count, retired_at)
                VALUES (?, ?, ?, ?, 'SYNC', ?, ?, 1, '{"attributes":[]}'::jsonb, 1, ?,
                        'RETIRED', 0, ?)
                """, versionId, ownerId, bindingId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), UUID.randomUUID(), java.sql.Timestamp.from(retiredAt));
    }

    private void insertRetiredRelationVersion(UUID versionId, UUID ownerId, SyncDataset dataset) {
        jdbc.update("""
                INSERT INTO data.ontology_relation_version
                    (id, owner_id, relation_id, relation_kind, from_object_type_id,
                     to_object_type_id, from_object_version_id, to_object_version_id,
                     source_dataset_kind, source_dataset_id, source_dataset_version_id,
                     source_snapshot_id, mapping_snapshot, mapping_revision, run_id,
                     status, row_count, skipped_count, retired_at)
                VALUES (?, ?, ?, 'FK', ?, ?, ?, ?, 'SYNC', ?, ?, 1, '{}'::jsonb, 1, ?,
                        'RETIRED', 0, 0, ?)
                """, versionId, ownerId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), dataset.datasetId(), dataset.versionId(),
                UUID.randomUUID(), java.sql.Timestamp.from(Instant.now().minusSeconds(600)));
    }

    private int count(String table, UUID versionId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + versionColumn(table) + " = ?",
                Integer.class,
                versionId
        );
    }

    private String versionColumn(String table) {
        return table.endsWith("_version") ? "id" : "version_id";
    }
}
