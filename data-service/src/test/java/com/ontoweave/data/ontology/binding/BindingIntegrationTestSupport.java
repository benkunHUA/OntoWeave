package com.ontoweave.data.ontology.binding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.sync.iceberg.HadoopCatalogFactory;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.encryption.EncryptionUtil;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class BindingIntegrationTestSupport {

    protected static final String TOKEN = "test-internal-token";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected HadoopCatalogFactory catalogs;

    @BeforeEach
    void cleanOntologyBinding() {
        jdbc.execute("""
                TRUNCATE TABLE
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

    protected UUID insertSyncDataset(UUID ownerId, String name) {
        return insertSyncDataset(ownerId, name, 1L, java.util.UUID.randomUUID(), """
                {"fields":[
                  {"name":"id","type":"string","required":true},
                  {"name":"name","type":"string","required":false}
                ]}
                """);
    }

    protected UUID insertSyncDataset(
            UUID ownerId,
            String name,
            long snapshotId,
            UUID versionId,
            String schemaJson
    ) {
        UUID connectionId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.data_connection
                    (id, owner_id, name, kind, config, conversation_id)
                VALUES (?, ?, ?, 'mysql', '{}'::jsonb, ?)
                """, connectionId, ownerId, "connection-" + name, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.dataset (id, connection_id, name)
                VALUES (?, ?, ?)
                """, datasetId, connectionId, name);
        jdbc.update("""
                INSERT INTO data.sync_task
                    (id, connection_id, dataset_id, schema_name, table_name, sync_mode,
                     primary_key_columns)
                VALUES (?, ?, ?, 'source', ?, 'snapshot', CAST('["id"]' AS jsonb))
                """, taskId, connectionId, datasetId, name);
        jdbc.update("UPDATE data.dataset SET sync_task_id = ? WHERE id = ?", taskId, datasetId);
        jdbc.update("""
                INSERT INTO data.dataset_version
                    (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                     iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                     byte_size, schema_json, column_count, committed_at, published_at,
                     version_source)
                VALUES (?, ?, 1, 'ontoweave', 'sync_ns', ?, ?, 0, false, 128,
                        CAST(? AS jsonb), 2, now(), now(), 'snapshot_end')
                """, versionId, datasetId, name, snapshotId, schemaJson);
        jdbc.update("UPDATE data.dataset SET current_version_id = ? WHERE id = ?",
                versionId, datasetId);
        return datasetId;
    }

    protected UUID insertPipelineDataset(UUID ownerId, String name) {
        UUID pipelineId = UUID.randomUUID();
        UUID pipelineVersionId = UUID.randomUUID();
        UUID buildId = UUID.randomUUID();
        UUID outputId = UUID.randomUUID();
        UUID buildOutputId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline (id, owner_id, conversation_id, name)
                VALUES (?, ?, ?, ?)
                """, pipelineId, ownerId, UUID.randomUUID(), "pipeline-" + name);
        jdbc.update("""
                INSERT INTO data.pipeline_version
                    (id, pipeline_id, version_number, definition, outputs, definition_hash,
                     input_refs, source_draft_revision, created_by)
                VALUES (?, ?, 1, '{}'::jsonb, '[]'::jsonb, ?, '[]'::jsonb, 1, ?)
                """, pipelineVersionId, pipelineId, "hash-" + UUID.randomUUID(), ownerId);
        jdbc.update("""
                INSERT INTO data.pipeline_build
                    (id, pipeline_id, pipeline_version_id, status, trigger_type, operation_id,
                     resolved_inputs, stats)
                VALUES (?, ?, ?, 'succeeded', 'manual', ?, '[]'::jsonb, '{}'::jsonb)
                """, buildId, pipelineId, pipelineVersionId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.pipeline_output (id, pipeline_id, node_id, output_type, name)
                VALUES (?, ?, ?, 'dataset', ?)
                """, outputId, pipelineId, UUID.randomUUID(), name);
        jdbc.update("""
                INSERT INTO data.pipeline_build_output
                    (id, build_id, output_id, output_type, status, result, stats)
                VALUES (?, ?, ?, 'dataset', 'succeeded', '{}'::jsonb, '{}'::jsonb)
                """, buildOutputId, buildId, outputId);
        jdbc.update("""
                INSERT INTO data.pipeline_dataset
                    (id, output_id, pipeline_id, name)
                VALUES (?, ?, ?, ?)
                """, datasetId, outputId, pipelineId, name);
        jdbc.update("""
                INSERT INTO data.pipeline_dataset_version
                    (id, dataset_id, build_output_id, version_number, iceberg_catalog,
                     iceberg_namespace, iceberg_table, iceberg_snapshot_id, schema_json,
                     row_count, byte_size, committed_at, published_at)
                VALUES (?, ?, ?, 1, 'ontoweave', 'pipeline_outputs', ?, 1,
                        CAST(? AS jsonb), 0, 256, now(), now())
                """, versionId, datasetId, buildOutputId, name, """
                {"fields":[
                  {"name":"id","type":"string","required":true},
                  {"name":"name","type":"string","required":false}
                ]}
                """);
        jdbc.update("UPDATE data.pipeline_dataset SET current_version_id = ? WHERE id = ?",
                versionId, datasetId);
        return datasetId;
    }

    protected long seedRows(
            String namespace,
            String tableName,
            Schema schema,
            List<Map<String, Object>> rows
    ) {
        try (HadoopCatalog catalog = catalogs.open()) {
            TableIdentifier identifier = TableIdentifier.of(namespace, tableName);
            Table table = catalog.tableExists(identifier)
                    ? catalog.loadTable(identifier)
                    : catalog.createTable(identifier, schema);
            append(table, schema, rows);
            return table.currentSnapshot() == null ? 0L : table.currentSnapshot().snapshotId();
        } catch (Exception exception) {
            throw new IllegalStateException("failed to seed Iceberg rows", exception);
        }
    }

    private void append(Table table, Schema schema, List<Map<String, Object>> rows) throws Exception {
        OutputFile outputFile = table.io().newOutputFile(
                table.location() + "/data/" + UUID.randomUUID() + ".parquet"
        );
        DataWriter<Record> writer = new GenericAppenderFactory(schema).newDataWriter(
                EncryptionUtil.plainAsEncryptedOutput(outputFile),
                FileFormat.PARQUET,
                null
        );
        for (Map<String, Object> values : rows) {
            GenericRecord record = GenericRecord.create(schema);
            values.forEach(record::setField);
            writer.write(record);
        }
        writer.close();
        table.newAppend().appendFile(writer.toDataFile()).commit();
    }

    protected Map<String, Long> ontologyCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of(
                "ontology_object_binding",
                "ontology_relation_binding",
                "ontology_relation_definition",
                "ontology_object_version",
                "ontology_relation_version",
                "ontology_index_run",
                "ontology_object_instance",
                "ontology_object_value",
                "ontology_relation_instance",
                "ontology_object_stage",
                "ontology_object_value_stage",
                "ontology_relation_stage"
        )) {
            Long count = jdbc.queryForObject("SELECT count(*) FROM data." + table, Long.class);
            counts.put(table, count == null ? 0L : count);
        }
        return counts;
    }

    protected String objectTypes() {
        return "OBJECT_TYPE";
    }

    protected OffsetDateTime timestamp(String value) {
        return OffsetDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
    }

    protected LocalDate date(String value) {
        return LocalDate.parse(value);
    }
}
