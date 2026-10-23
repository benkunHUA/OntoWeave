package com.ontoweave.data.ontology.foundation;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OntologyMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    void v9MigratesAnExistingP1AndP2Database() throws Exception {
        flyway().target(MigrationVersion.fromVersion("8")).load().migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        )) {
            UUID connectionId = UUID.randomUUID();
            UUID syncDatasetId = UUID.randomUUID();
            UUID syncVersionId = UUID.randomUUID();
            execute(connection, """
                    INSERT INTO data.data_connection (id, owner_id, name, kind, conversation_id)
                    VALUES ('%s', '%s', 'existing', 'mysql', '%s')
                    """.formatted(connectionId, UUID.randomUUID(), UUID.randomUUID()));
            execute(connection, """
                    INSERT INTO data.dataset (id, connection_id, name)
                    VALUES ('%s', '%s', 'existing_sync')
                    """.formatted(syncDatasetId, connectionId));
            execute(connection, """
                    INSERT INTO data.dataset_version
                        (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                         iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                         byte_size, schema_json, column_count, committed_at, published_at,
                         version_source)
                    VALUES ('%s', '%s', 1, 'ontoweave', 'ns', 'existing_sync', 1, 1, false,
                            8, '{}'::jsonb, 1, now(), now(), 'snapshot_end')
                    """.formatted(syncVersionId, syncDatasetId));
            execute(connection, """
                    UPDATE data.dataset SET current_version_id = '%s' WHERE id = '%s'
                    """.formatted(syncVersionId, syncDatasetId));
            UUID pipelineId = UUID.randomUUID();
            UUID outputId = UUID.randomUUID();
            UUID pipelineDatasetId = UUID.randomUUID();
            execute(connection, """
                    INSERT INTO data.pipeline (id, owner_id, conversation_id, name)
                    VALUES ('%s', '%s', '%s', 'existing_pipeline')
                    """.formatted(pipelineId, UUID.randomUUID(), UUID.randomUUID()));
            execute(connection, """
                    INSERT INTO data.pipeline_output
                        (id, pipeline_id, node_id, output_type, name)
                    VALUES ('%s', '%s', '%s', 'dataset', 'existing_output')
                    """.formatted(outputId, pipelineId, UUID.randomUUID()));
            execute(connection, """
                    INSERT INTO data.pipeline_dataset (id, output_id, pipeline_id, name)
                    VALUES ('%s', '%s', '%s', 'existing_pipeline_dataset')
                    """.formatted(pipelineDatasetId, outputId, pipelineId));
        }

        flyway().load().migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        )) {
            Set<String> tables = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("""
                         SELECT table_name FROM information_schema.tables
                         WHERE table_schema = 'data'
                         """)) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
            assertThat(tables).contains(
                    "ontology_object_binding",
                    "ontology_relation_definition",
                    "ontology_relation_binding",
                    "ontology_object_version",
                    "ontology_relation_version",
                    "ontology_index_run",
                    "ontology_object_instance",
                    "ontology_object_value",
                    "ontology_relation_instance",
                    "ontology_object_stage",
                    "ontology_object_value_stage",
                    "ontology_relation_stage"
            );
            assertThat(count(connection, "data.dataset")).isEqualTo(1);
            assertThat(count(connection, "data.pipeline_dataset")).isEqualTo(1);
            assertThat(count(connection, "data.ontology_object_binding")).isZero();
            assertThat(extensionInstalled(connection, "pg_trgm")).isTrue();
        }
    }

    private FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("data")
                .defaultSchema("data")
                .createSchemas(true)
                .locations("classpath:db/migration");
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private boolean extensionInstalled(Connection connection, String extension) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT count(*) FROM pg_extension WHERE extname = ?"
        )) {
            statement.setString(1, extension);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) > 0;
            }
        }
    }
}
