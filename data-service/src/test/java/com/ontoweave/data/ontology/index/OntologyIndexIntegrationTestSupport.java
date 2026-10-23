package com.ontoweave.data.ontology.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class OntologyIndexIntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper mapper;

    @BeforeEach
    void cleanOntologyIndex() {
        jdbc.execute("""
                TRUNCATE TABLE
                    data.outbox_event,
                    data.ontology_object_stage,
                    data.ontology_object_value_stage,
                    data.ontology_relation_stage,
                    data.ontology_object_instance,
                    data.ontology_object_value,
                    data.ontology_relation_instance,
                    data.ontology_object_version,
                    data.ontology_relation_version,
                    data.ontology_index_run,
                    data.ontology_object_binding,
                    data.ontology_relation_binding,
                    data.ontology_relation_definition
                CASCADE
                """);
    }

    protected SyncDataset syncDataset(UUID ownerId, String schemaJson, long snapshotId) {
        UUID connectionId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO data.data_connection
                    (id, owner_id, name, kind, config, status, conversation_id)
                VALUES (?, ?, ?, 'mysql', '{}'::jsonb, 'active', ?)
                """, connectionId, ownerId, "connection-" + suffix, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.dataset (id, connection_id, name)
                VALUES (?, ?, ?)
                """, datasetId, connectionId, "dataset-" + suffix);
        jdbc.update("""
                INSERT INTO data.dataset_version
                    (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                     iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                     byte_size, schema_json, column_count, committed_at, published_at,
                     version_source)
                VALUES (?, ?, 1, 'ontoweave', 'test', ?, ?, 0, false, 0,
                        CAST(? AS jsonb), 0, now(), now(), 'scheduled')
                """, versionId, datasetId, "table-" + suffix, snapshotId, schemaJson);
        jdbc.update("UPDATE data.dataset SET current_version_id = ? WHERE id = ?",
                versionId, datasetId);
        return new SyncDataset(connectionId, datasetId, versionId);
    }

    protected UUID addSyncDatasetVersion(SyncDataset dataset, String schemaJson, long snapshotId) {
        UUID versionId = UUID.randomUUID();
        Integer next = jdbc.queryForObject("""
                SELECT coalesce(max(version_number), 0) + 1
                FROM data.dataset_version WHERE dataset_id = ?
                """, Integer.class, dataset.datasetId());
        jdbc.update("""
                INSERT INTO data.dataset_version
                    (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                     iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                     byte_size, schema_json, column_count, committed_at, published_at,
                     version_source)
                VALUES (?, ?, ?, 'ontoweave', 'test', ?, ?, 0, false, 0,
                        CAST(? AS jsonb), 0, now(), now(), 'scheduled')
                """, versionId, dataset.datasetId(), next, "table-" + dataset.datasetId(),
                snapshotId, schemaJson);
        jdbc.update("UPDATE data.dataset SET current_version_id = ? WHERE id = ?",
                versionId, dataset.datasetId());
        return versionId;
    }

    protected void insertObjectBinding(
            UUID bindingId,
            UUID ownerId,
            UUID objectTypeId,
            UUID datasetId,
            UUID datasetVersionId,
            UUID currentVersionId,
            String validationState
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_object_binding
                    (id, owner_id, object_type_id, draft_dataset_kind, draft_dataset_id,
                     draft_mapping, active_dataset_kind, active_dataset_id,
                     active_dataset_version_id, active_mapping_revision,
                     current_index_version_id, validation_state)
                VALUES (?, ?, ?, 'SYNC', ?, '{}'::jsonb, 'SYNC', ?, ?, 1, ?, ?)
                """, bindingId, ownerId, objectTypeId, datasetId, datasetId,
                datasetVersionId, currentVersionId, validationState);
    }

    protected void insertRelationBinding(
            UUID bindingId,
            UUID ownerId,
            UUID relationId,
            SyncDataset dataset,
            UUID currentVersionId
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_relation_binding
                    (id, owner_id, relation_id, draft_dataset_kind, draft_dataset_id,
                     draft_mapping, active_dataset_kind, active_dataset_id,
                     active_dataset_version_id, active_mapping_revision,
                     current_index_version_id, validation_state)
                VALUES (?, ?, ?, 'SYNC', ?, '{}'::jsonb, 'SYNC', ?, ?, 1, ?, 'OK')
                """, bindingId, ownerId, relationId, dataset.datasetId(), dataset.datasetId(),
                dataset.versionId(), currentVersionId);
    }

    protected void insertObjectVersion(
            UUID versionId,
            UUID ownerId,
            UUID bindingId,
            UUID objectTypeId,
            SyncDataset dataset,
            JsonNode mapping,
            String status
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_object_version
                    (id, owner_id, binding_id, object_type_id, source_dataset_kind,
                     source_dataset_id, source_dataset_version_id, source_snapshot_id,
                     mapping_snapshot, mapping_revision, run_id, status, row_count)
                VALUES (?, ?, ?, ?, 'SYNC', ?, ?, 1, CAST(? AS jsonb), 1, ?, ?, 0)
                """, versionId, ownerId, bindingId, objectTypeId, dataset.datasetId(),
                dataset.versionId(), mapping.toString(), UUID.randomUUID(), status);
    }

    protected JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    protected record SyncDataset(UUID connectionId, UUID datasetId, UUID versionId) {
    }
}
