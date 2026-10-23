package com.ontoweave.data.ontology.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.index.worker.OntologyIndexExecutor;
import com.ontoweave.data.ontology.query.service.ObjectQueryService;
import com.ontoweave.data.ontology.query.service.RelationQueryService;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class P3AcceptanceSupport {

    protected static final String WORKER = "p3-acceptance-worker";
    protected static final String DATASET_SCHEMA = "{\"fields\":[]}";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    static {
        if (Boolean.getBoolean("p3.acceptance") || Boolean.getBoolean("p3.performance")) {
            POSTGRES.start();
            Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
        }
    }

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

    @Autowired
    protected OntologyIndexRunRepository runs;

    @Autowired
    protected OntologyIndexExecutor executor;

    @Autowired
    protected ObjectQueryService objectQueries;

    @Autowired
    protected RelationQueryService relationQueries;

    protected void truncateOntology() {
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
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS ix_p3_acceptance_relation_stage_pair
                ON data.ontology_relation_stage
                    (run_id, relation_id, from_primary_key, to_primary_key)
                """);
    }

    protected DatasetFixture createDataset(
            UUID ownerId,
            String table,
            long snapshotId,
            int rowCount,
            int columnCount
    ) {
        UUID connectionId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.data_connection
                    (id, owner_id, name, kind, config, status, conversation_id)
                VALUES (?, ?, ?, 'mysql', '{}'::jsonb, 'active', ?)
                """, connectionId, ownerId, "p3-connection-" + connectionId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.dataset (id, connection_id, name)
                VALUES (?, ?, ?)
                """, datasetId, connectionId, "p3-dataset-" + datasetId);
        jdbc.update("""
                INSERT INTO data.dataset_version
                    (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                     iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                     byte_size, schema_json, column_count, committed_at, published_at,
                     version_source)
                VALUES (?, ?, 1, 'ontoweave', 'p3', ?, ?, ?, false, 0,
                        CAST(? AS jsonb), ?, now(), now(), 'scheduled')
                """, versionId, datasetId, table, snapshotId, rowCount, DATASET_SCHEMA, columnCount);
        jdbc.update("UPDATE data.dataset SET current_version_id = ? WHERE id = ?",
                versionId, datasetId);
        return new DatasetFixture(connectionId, datasetId, table, versionId);
    }

    protected UUID addDatasetVersion(
            DatasetFixture dataset,
            long snapshotId,
            int rowCount,
            int columnCount
    ) {
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
                VALUES (?, ?, ?, 'ontoweave', 'p3', ?, ?, ?, false, 0,
                        CAST(? AS jsonb), ?, now(), now(), 'scheduled')
                """, versionId, dataset.datasetId(), next, dataset.table(), snapshotId,
                rowCount, DATASET_SCHEMA, columnCount);
        jdbc.update("UPDATE data.dataset SET current_version_id = ? WHERE id = ?",
                versionId, dataset.datasetId());
        return versionId;
    }

    protected ObjectBindingFixture createObjectBinding(
            UUID ownerId,
            UUID objectTypeId,
            DatasetFixture dataset,
            String mapping
    ) {
        UUID bindingId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_object_binding
                    (id, owner_id, object_type_id, status, revision,
                     draft_dataset_kind, draft_dataset_id, draft_mapping,
                     active_dataset_kind, active_dataset_id, active_dataset_version_id,
                     active_mapping_revision, validation_state)
                VALUES (?, ?, ?, 'ACTIVE', 1, 'SYNC', ?, CAST(? AS jsonb),
                        'SYNC', ?, ?, 1, 'OK')
                """, bindingId, ownerId, objectTypeId, dataset.datasetId(), mapping,
                dataset.datasetId(), dataset.versionId());
        return new ObjectBindingFixture(bindingId, objectTypeId, dataset, mapping);
    }

    protected RelationBindingFixture createRelationBinding(
            UUID ownerId,
            UUID relationId,
            DatasetFixture dataset,
            String mapping,
            UUID currentVersionId
    ) {
        UUID bindingId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_relation_binding
                    (id, owner_id, relation_id, status, revision,
                     draft_dataset_kind, draft_dataset_id, draft_mapping,
                     active_dataset_kind, active_dataset_id, active_dataset_version_id,
                     active_mapping_revision, current_index_version_id, validation_state)
                VALUES (?, ?, ?, 'ACTIVE', 1, 'SYNC', ?, CAST(? AS jsonb),
                        'SYNC', ?, ?, 1, ?, 'OK')
                """, bindingId, ownerId, relationId, dataset.datasetId(), mapping,
                dataset.datasetId(), dataset.versionId(), currentVersionId);
        return new RelationBindingFixture(bindingId, relationId, dataset, mapping);
    }

    protected OntologyIndexRunEntity insertRunningObjectRun(
            UUID ownerId,
            UUID objectTypeId,
            UUID bindingId,
            UUID datasetVersionId,
            String mapping
    ) {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id,
                     target_dataset_kind, target_dataset_version_id,
                     mapping_snapshot, mapping_revision, trigger_type, status, phase,
                     started_at, heartbeat_at, lease_owner, lease_expires_at, attempt)
                VALUES (?, ?, 'OBJECT_TYPE', ?, ?, 'SYNC', ?, CAST(? AS jsonb), 1,
                        'MANUAL', 'RUNNING', 'READING', now(), now(), ?,
                        now() + interval '10 minutes', 1)
                """, runId, ownerId, objectTypeId, bindingId, datasetVersionId, mapping, WORKER);
        return runs.findById(runId).orElseThrow();
    }

    protected OntologyIndexRunEntity insertRunningRelationRun(
            UUID ownerId,
            UUID relationId,
            UUID bindingId,
            UUID datasetVersionId,
            String mapping
    ) {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id,
                     target_dataset_kind, target_dataset_version_id,
                     mapping_snapshot, mapping_revision, trigger_type, status, phase,
                     started_at, heartbeat_at, lease_owner, lease_expires_at, attempt)
                VALUES (?, ?, 'RELATION', ?, ?, 'SYNC', ?, CAST(? AS jsonb), 1,
                        'MANUAL', 'RUNNING', 'READING', now(), now(), ?,
                        now() + interval '10 minutes', 1)
                """, runId, ownerId, relationId, bindingId, datasetVersionId, mapping, WORKER);
        return runs.findById(runId).orElseThrow();
    }

    protected void executeObjectRun(OntologyIndexRunEntity run) {
        executor.execute(run, WORKER);
    }

    protected UUID currentObjectVersion(UUID bindingId) {
        return jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_object_binding WHERE id = ?
                """, UUID.class, bindingId);
    }

    protected UUID currentRelationVersion(UUID bindingId) {
        return jdbc.queryForObject("""
                SELECT current_index_version_id FROM data.ontology_relation_binding WHERE id = ?
                """, UUID.class, bindingId);
    }

    protected long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0L : value;
    }

    protected long objectRows(java.util.UUID versionId) {
        return count("""
                SELECT count(*) FROM data.ontology_object_instance WHERE version_id = ?
                """, versionId);
    }

    protected record DatasetFixture(
            UUID connectionId,
            UUID datasetId,
            String table,
            UUID versionId
    ) {
    }

    protected record ObjectBindingFixture(
            UUID bindingId,
            UUID objectTypeId,
            DatasetFixture dataset,
            String mapping
    ) {
    }

    protected record RelationBindingFixture(
            UUID bindingId,
            UUID relationId,
            DatasetFixture dataset,
            String mapping
    ) {
    }
}
