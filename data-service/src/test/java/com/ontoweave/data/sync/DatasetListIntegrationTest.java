package com.ontoweave.data.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatasetListIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE data.data_connection CASCADE");
    }

    @Test
    void listsOwnedSyncDatasetsWithPaginationAndVersionMetadata() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID connectionId = createConnection(owner, "owner-connection");
        UUID otherConnectionId = createConnection(otherOwner, "other-connection");

        UUID firstDataset = createSyncDataset(
                connectionId, "sales.orders", "2026-01-01T00:00:00Z", "sales", "orders"
        );
        UUID firstVersion = addVersion(
                connectionId,
                firstDataset,
                1,
                10,
                "{\"type\":\"struct\",\"fields\":[{\"name\":\"id\",\"type\":\"long\",\"required\":true}]}"
        );
        UUID currentVersion = addVersion(
                connectionId,
                firstDataset,
                2,
                25,
                """
                        {"type":"struct","fields":[
                          {"name":"id","type":"long","required":true},
                          {"name":"name","type":"string","required":false}
                        ]}
                        """
        );
        jdbc.update("""
                UPDATE data.dataset
                SET current_version_id = ?, active_iceberg_catalog = 'catalog',
                    active_iceberg_namespace = 'ns', active_iceberg_table = 'orders'
                WHERE id = ?
                """, currentVersion, firstDataset);

        UUID secondDataset = createSyncDataset(
                connectionId, "sales.customers", "2026-01-02T00:00:00Z", "sales", "customers"
        );
        createSyncDataset(connectionId, "sales.unbound", "2026-01-03T00:00:00Z", null, null);
        createSyncDataset(
                otherConnectionId, "sales.hidden", "2025-12-01T00:00:00Z", "sales", "hidden"
        );

        JsonNode page = list(owner, 1, 0);

        assertThat(page.path("total").asInt()).isEqualTo(2);
        assertThat(page.path("limit").asInt()).isEqualTo(1);
        assertThat(page.path("offset").asInt()).isZero();
        JsonNode item = page.path("items").get(0);
        assertThat(item.path("id").asText()).isEqualTo(firstDataset.toString());
        assertThat(item.path("connectionId").asText()).isEqualTo(connectionId.toString());
        assertThat(item.path("name").asText()).isEqualTo("sales.orders");
        assertThat(item.path("currentVersionId").asText()).isEqualTo(currentVersion.toString());
        assertThat(item.path("versionCount").asInt()).isEqualTo(2);
        assertThat(item.path("sourceSchema").asText()).isEqualTo("sales");
        assertThat(item.path("sourceTable").asText()).isEqualTo("orders");
        assertThat(item.path("createdAt").asText()).startsWith("2026-01-01T00:00:00");
        assertThat(item.path("currentVersion").path("id").asText()).isEqualTo(currentVersion.toString());
        assertThat(item.path("currentVersion").path("versionNumber").asInt()).isEqualTo(2);
        assertThat(item.path("currentVersion").path("rowCount").asLong()).isEqualTo(25);
        assertThat(item.path("currentVersion").path("columnCount").asInt()).isEqualTo(2);
        assertThat(item.path("currentVersion").path("schema").path("fields").get(1).path("name").asText())
                .isEqualTo("name");

        JsonNode secondPage = list(owner, 1, 1);
        assertThat(secondPage.path("items")).hasSize(1);
        assertThat(secondPage.path("items").get(0).path("id").asText())
                .isEqualTo(secondDataset.toString());
        assertThat(secondPage.path("items").get(0).path("currentVersion").isNull()).isTrue();
        assertThat(secondPage.path("items").get(0).path("versionCount").asInt()).isZero();

        JsonNode otherPage = list(otherOwner, 20, 0);
        assertThat(otherPage.path("total").asInt()).isEqualTo(1);
        assertThat(otherPage.path("items").get(0).path("name").asText()).isEqualTo("sales.hidden");
        assertThat(otherPage.path("items").get(0).path("versionCount").asInt()).isZero();

        assertThat(firstVersion).isNotNull();
    }

    @Test
    void rejectsPaginationOutsideThePublicBounds() throws Exception {
        UUID owner = UUID.randomUUID();

        JsonNode body = json(mvc.perform(get("/internal/v1/datasets")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .param("limit", "201"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("INVALID_PAGINATION");

        mvc.perform(get("/internal/v1/datasets")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/internal/v1/datasets")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode list(UUID owner, int limit, int offset) throws Exception {
        return json(mvc.perform(get("/internal/v1/datasets")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .param("limit", Integer.toString(limit))
                        .param("offset", Integer.toString(offset)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private UUID createConnection(UUID owner, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.data_connection
                    (id, owner_id, name, kind, conversation_id, status)
                VALUES (?, ?, ?, 'mysql', ?, 'active')
                """, id, owner, name, UUID.randomUUID());
        return id;
    }

    private UUID createSyncDataset(
            UUID connectionId,
            String name,
            String createdAt,
            String sourceSchema,
            String sourceTable
    ) {
        UUID datasetId = UUID.randomUUID();
        Timestamp timestamp = Timestamp.from(Instant.parse(createdAt));
        jdbc.update("""
                INSERT INTO data.dataset (id, connection_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, datasetId, connectionId, name, timestamp, timestamp);
        if (sourceSchema == null) {
            return datasetId;
        }
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.sync_task
                    (id, connection_id, dataset_id, schema_name, table_name, sync_mode,
                     primary_key_columns, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'snapshot', CAST('["id"]' AS jsonb), ?, ?)
                """, taskId, connectionId, datasetId, sourceSchema, sourceTable, timestamp, timestamp);
        jdbc.update("UPDATE data.dataset SET sync_task_id = ? WHERE id = ?", taskId, datasetId);
        return datasetId;
    }

    private UUID addVersion(
            UUID connectionId,
            UUID datasetId,
            int versionNumber,
            long rowCount,
            String schema
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.dataset_version
                    (id, dataset_id, version_number, iceberg_catalog, iceberg_namespace,
                     iceberg_table, iceberg_snapshot_id, row_count, row_count_estimated,
                     byte_size, schema_json, column_count, committed_at, published_at,
                     version_source)
                VALUES (?, ?, ?, 'catalog', ?, ?, ?, ?, false, 1024, CAST(? AS jsonb), 2,
                        now(), now(), 'snapshot_end')
                """, id, datasetId, versionNumber, "ns-" + connectionId, "table-" + datasetId,
                versionNumber, rowCount, schema);
        return id;
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }
}
