package com.ontoweave.data.pipeline.source;

import com.ontoweave.data.pipeline.api.dto.SourceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PipelineSourceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    PipelineSourceService service;

    @Autowired
    JdbcTemplate jdbc;

    private SourceFixture fixture;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE data.pipeline CASCADE");
        jdbc.execute("TRUNCATE TABLE data.data_connection CASCADE");
        jdbc.execute("TRUNCATE TABLE data.outbox_event");
        fixture = seed();
    }

    @Test
    void readsPublishedSourceChainAndKeepsBuildTimeDisplaySnapshot() {
        SourceResponse first = service.get(
                fixture.pipelineId(), fixture.datasetId(), null, fixture.ownerId()
        );

        assertThat(first.versionId()).isEqualTo(fixture.datasetVersionId());
        assertThat(first.output().name()).isEqualTo("orders_clean");
        assertThat(first.build().status()).isEqualTo("succeeded");
        assertThat(first.pipelineVersion().versionNumber()).isEqualTo(7);
        assertThat(first.inputs()).singleElement().satisfies(input -> {
            assertThat(input.metadataStatus()).isEqualTo(MetadataStatus.AVAILABLE);
            assertThat(input.datasetVersionId()).isEqualTo(fixture.inputVersionId());
            assertThat(input.datasetVersionNumber()).isEqualTo(12);
            assertThat(input.datasetName()).isEqualTo("orders");
            assertThat(input.connectionName()).isEqualTo("prod-mysql");
            assertThat(input.sourceSchemaName()).isEqualTo("sales");
            assertThat(input.sourceTableName()).isEqualTo("orders");
        });

        jdbc.update("UPDATE data.dataset SET name = 'orders-renamed' WHERE id = ?", fixture.inputDatasetId());
        SourceResponse historical = service.get(
                fixture.pipelineId(), fixture.datasetId(), fixture.datasetVersionId(), fixture.ownerId()
        );

        assertThat(historical.inputs()).singleElement().satisfies(input -> {
            assertThat(input.datasetName()).isEqualTo("orders");
            assertThat(input.datasetVersionId()).isEqualTo(fixture.inputVersionId());
        });
    }

    @Test
    void hidesInputMetadataWhenTheConnectionBelongsToAnotherOwner() {
        jdbc.update("UPDATE data.data_connection SET owner_id = ? WHERE id = ?",
                UUID.randomUUID(), fixture.connectionId());

        SourceResponse response = service.get(
                fixture.pipelineId(), fixture.datasetId(), fixture.datasetVersionId(), fixture.ownerId()
        );

        assertThat(response.inputs()).singleElement().satisfies(input -> {
            assertThat(input.metadataStatus()).isEqualTo(MetadataStatus.RESTRICTED);
            assertThat(input.datasetName()).isNull();
            assertThat(input.connectionName()).isNull();
            assertThat(input.sourceSchemaName()).isNull();
            assertThat(input.sourceTableName()).isNull();
        });
    }

    @Test
    void keepsHistoricalInputVersionNumberAfterANewInputVersionIsPublished() {
        UUID nextVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.dataset_version
                    (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                     iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                     byte_size, schema_json, column_count, committed_at, published_at, version_source)
                VALUES (?, ?, 13, 'catalog', 'ns', 'orders', 13, 200, false,
                        2048, '{}'::jsonb, 1, now(), now(), 'snapshot_end')
                """, nextVersionId, fixture.inputDatasetId());
        jdbc.update("UPDATE data.dataset SET current_version_id = ? WHERE id = ?",
                nextVersionId, fixture.inputDatasetId());

        SourceResponse historical = service.get(
                fixture.pipelineId(), fixture.datasetId(), fixture.datasetVersionId(), fixture.ownerId()
        );

        assertThat(historical.inputs()).singleElement().satisfies(input -> {
            assertThat(input.datasetVersionId()).isEqualTo(fixture.inputVersionId());
            assertThat(input.datasetVersionNumber()).isEqualTo(12);
        });
    }

    @Test
    void readsSourcesForAnArchivedPipeline() {
        jdbc.update("UPDATE data.pipeline SET status = 'archived', archived_at = now() WHERE id = ?",
                fixture.pipelineId());

        SourceResponse response = service.get(
                fixture.pipelineId(), fixture.datasetId(), fixture.datasetVersionId(), fixture.ownerId()
        );

        assertThat(response.output().name()).isEqualTo("orders_clean");
        assertThat(response.inputs()).hasSize(1);
    }

    private SourceFixture seed() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID outputId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID pipelineVersionId = UUID.randomUUID();
        UUID buildId = UUID.randomUUID();
        UUID buildOutputId = UUID.randomUUID();
        UUID datasetVersionId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID inputDatasetId = UUID.randomUUID();
        UUID inputVersionId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        jdbc.update("""
                INSERT INTO data.pipeline (id, owner_id, conversation_id, name, status)
                VALUES (?, ?, ?, 'pipeline', 'draft')
                """, pipelineId, ownerId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.pipeline_output (id, pipeline_id, node_id, output_type, name)
                VALUES (?, ?, ?, 'dataset', 'orders_clean')
                """, outputId, pipelineId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.pipeline_dataset
                    (id, output_id, pipeline_id, name, write_mode, primary_key_columns, expectations)
                VALUES (?, ?, ?, 'orders_clean', 'snapshot', '[]'::jsonb, '[]'::jsonb)
                """, datasetId, outputId, pipelineId);
        jdbc.update("""
                INSERT INTO data.pipeline_version
                    (id, pipeline_id, version_number, definition, outputs, definition_hash,
                     input_refs, source_draft_revision, created_by)
                VALUES (?, ?, 7, '{}'::jsonb, '[]'::jsonb, 'hash-source', '[]'::jsonb, 1, ?)
                """, pipelineVersionId, pipelineId, ownerId);
        jdbc.update("""
                INSERT INTO data.pipeline_build
                    (id, pipeline_id, pipeline_version_id, status, trigger_type, operation_id,
                     resolved_inputs, stats)
                VALUES (?, ?, ?, 'succeeded', 'manual', ?, CAST(? AS jsonb), '{}'::jsonb)
                """, buildId, pipelineId, pipelineVersionId, UUID.randomUUID(), resolvedInputs(
                inputDatasetId, inputVersionId, connectionId
        ));
        jdbc.update("""
                INSERT INTO data.pipeline_build_output
                    (id, build_id, output_id, output_type, status, result, stats)
                VALUES (?, ?, ?, 'dataset', 'succeeded', '{}'::jsonb, '{}'::jsonb)
                """, buildOutputId, buildId, outputId);
        jdbc.update("""
                INSERT INTO data.pipeline_dataset_version
                    (id, dataset_id, build_output_id, version_number, iceberg_catalog,
                     iceberg_namespace, iceberg_table, iceberg_snapshot_id, schema_json,
                     row_count, byte_size, committed_at, published_at)
                VALUES (?, ?, ?, 3, 'catalog', 'ns', 'orders_clean', 1, '{}'::jsonb,
                        1200, 1024, ?, ?)
                """, datasetVersionId, datasetId, buildOutputId, now, now);
        jdbc.update("UPDATE data.pipeline_dataset SET current_version_id = ? WHERE id = ?",
                datasetVersionId, datasetId);

        jdbc.update("""
                INSERT INTO data.data_connection (id, owner_id, conversation_id, name, kind, config)
                VALUES (?, ?, ?, 'prod-mysql', 'mysql', '{}'::jsonb)
                """, connectionId, ownerId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO data.dataset (id, connection_id, name)
                VALUES (?, ?, 'orders')
                """, inputDatasetId, connectionId);
        jdbc.update("""
                INSERT INTO data.sync_task
                    (id, connection_id, dataset_id, schema_name, table_name, sync_mode,
                     primary_key_columns)
                VALUES (?, ?, ?, 'sales', 'orders', 'snapshot', '[]'::jsonb)
                """, UUID.randomUUID(), connectionId, inputDatasetId);
        jdbc.update("""
                INSERT INTO data.dataset_version
                    (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                     iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                     byte_size, schema_json, column_count, committed_at, published_at, version_source)
                VALUES (?, ?, 12, 'catalog', 'ns', 'orders', 12, 100, false,
                        1024, '{}'::jsonb, 1, ?, ?, 'snapshot_end')
                """, inputVersionId, inputDatasetId, now, now);
        jdbc.update("UPDATE data.dataset SET current_version_id = ? WHERE id = ?",
                inputVersionId, inputDatasetId);

        return new SourceFixture(
                ownerId,
                pipelineId,
                datasetId,
                datasetVersionId,
                connectionId,
                inputDatasetId,
                inputVersionId
        );
    }

    private String resolvedInputs(UUID inputDatasetId, UUID inputVersionId, UUID connectionId) {
        return """
                [{
                  "datasetKind":"sync",
                  "datasetId":"%s",
                  "datasetVersionId":"%s",
                  "datasetVersionNumber":12,
                  "schemaHash":"schema-hash",
                  "displaySnapshot":{
                    "datasetName":"orders",
                    "datasetVersionNumber":12,
                    "connectionId":"%s",
                    "connectionName":"prod-mysql",
                    "sourceSchemaName":"sales",
                    "sourceTableName":"orders"
                  }
                }]
                """.formatted(inputDatasetId, inputVersionId, connectionId);
    }

    private record SourceFixture(
            UUID ownerId,
            UUID pipelineId,
            UUID datasetId,
            UUID datasetVersionId,
            UUID connectionId,
            UUID inputDatasetId,
            UUID inputVersionId
    ) {
    }
}
