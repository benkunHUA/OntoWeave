package com.ontoweave.data.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("demo")
            .withUsername("reader")
            .withPassword("reader");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SyncRunRepository runs;

    @BeforeAll
    static void createSourceTables() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE customers (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
            statement.execute("INSERT INTO customers (id, name) VALUES (1, 'alice'), (2, 'bob')");
            statement.execute("CREATE TABLE no_pk (name VARCHAR(100) NOT NULL)");
        }
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DELETE FROM data.dataset_version");
        jdbc.execute("DELETE FROM data.sync_run");
        jdbc.execute("DELETE FROM data.sync_task");
        jdbc.execute("DELETE FROM data.dataset");
        jdbc.execute("DELETE FROM data.outbox_event");
        jdbc.execute("DELETE FROM data.data_connection_secret");
        jdbc.execute("DELETE FROM data.data_connection");
    }

    @Test
    void createsTaskTriggersRunAndExposesDatasetState() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID connectionId = createConnection(owner);

        JsonNode tasks = json(mvc.perform(post("/internal/v1/data-connections/{id}/sync-tasks", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"schema":"demo","table":"customers","syncMode":"snapshot"}]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(tasks.path("tasks").size()).isEqualTo(1);
        assertThat(tasks.path("tasks").get(0).path("primaryKeyColumns").get(0).asText()).isEqualTo("id");
        UUID datasetId = UUID.fromString(tasks.path("tasks").get(0).path("datasetId").asText());

        UUID operationId = UUID.randomUUID();
        JsonNode triggered = json(mvc.perform(post("/internal/v1/data-connections/{id}/sync-runs", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operationId", operationId,
                                "tables", java.util.List.of(Map.of("schema", "demo", "table", "customers"))
                        ))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
        UUID runId = UUID.fromString(triggered.path("runs").get(0).path("id").asText());
        assertThat(triggered.path("runs").get(0).path("status").asText()).isEqualTo("queued");
        assertThat(triggered.path("runs").get(0).path("icebergTable").asText()).startsWith("ds_");

        JsonNode run = json(mvc.perform(get("/internal/v1/sync-runs/{id}", runId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(run.path("operationId").asText()).isEqualTo(operationId.toString());

        JsonNode dataset = json(mvc.perform(get("/internal/v1/datasets/{id}", datasetId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(dataset.path("versions").isEmpty()).isTrue();
        assertThat(dataset.path("name").asText()).isEqualTo("demo.customers");

        JsonNode stopped = json(mvc.perform(post("/internal/v1/sync-runs/{id}/stop", runId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
        assertThat(stopped.path("status").asText()).isEqualTo("stopping");

        Integer queuedEvents = jdbc.queryForObject(
                "SELECT count(*) FROM data.outbox_event WHERE event_type = 'sync.run.queued'",
                Integer.class
        );
        assertThat(queuedEvents).isEqualTo(1);
    }

    @Test
    void rejectsTableWithoutPrimaryKeyBeforeCreatingAnything() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID connectionId = createConnection(owner);

        JsonNode body = json(mvc.perform(post("/internal/v1/data-connections/{id}/sync-tasks", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"schema":"demo","table":"no_pk","syncMode":"snapshot"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString());

        assertThat(body.path("code").asText()).isEqualTo("TABLE_VALIDATION_FAILED");
        Integer tasks = jdbc.queryForObject("SELECT count(*) FROM data.sync_task", Integer.class);
        assertThat(tasks).isZero();
    }

    @Test
    void lateDispatchAckDoesNotStrandRequeuedRun() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID connectionId = createConnection(owner);
        mvc.perform(post("/internal/v1/data-connections/{id}/sync-tasks", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"schema":"demo","table":"customers","syncMode":"snapshot"}]}
                                """))
                .andExpect(status().isOk());

        JsonNode triggered = json(mvc.perform(post("/internal/v1/data-connections/{id}/sync-runs", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(triggerBody(UUID.randomUUID())))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
        UUID runId = UUID.fromString(triggered.path("runs").get(0).path("id").asText());
        Instant now = Instant.now();

        assertThat(runs.beginDispatch(runId, 5)).isEqualTo(1);
        assertThat(runs.releaseDispatch(runId, SyncRunStatus.QUEUED, now)).isEqualTo(1);
        assertThat(runs.confirmDispatched(runId, now)).isZero();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT dispatched_at, dispatch_attempts FROM data.sync_run WHERE id = ?",
                runId
        );
        assertThat(row.get("dispatched_at")).isNull();
        assertThat(row.get("dispatch_attempts")).isEqualTo(0);
    }

    @Test
    void rejectsAnotherRunWhileTheSameTaskIsActive() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID connectionId = createConnection(owner);
        mvc.perform(post("/internal/v1/data-connections/{id}/sync-tasks", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tables":[{"schema":"demo","table":"customers","syncMode":"snapshot"}]}
                                """))
                .andExpect(status().isOk());

        UUID firstOperation = UUID.randomUUID();
        JsonNode first = json(mvc.perform(post("/internal/v1/data-connections/{id}/sync-runs", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(triggerBody(firstOperation)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());
        UUID runId = UUID.fromString(first.path("runs").get(0).path("id").asText());

        JsonNode conflict = json(mvc.perform(post("/internal/v1/data-connections/{id}/sync-runs", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(triggerBody(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString());

        assertThat(conflict.path("code").asText()).isEqualTo("SYNC_RUN_ACTIVE");
        Integer runCount = jdbc.queryForObject("SELECT count(*) FROM data.sync_run", Integer.class);
        assertThat(runCount).isEqualTo(1);

        jdbc.update("UPDATE data.sync_run SET status = 'stopped' WHERE id = ?", runId);
        mvc.perform(post("/internal/v1/data-connections/{id}/sync-runs", connectionId)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(triggerBody(UUID.randomUUID())))
                .andExpect(status().isAccepted());
    }

    private UUID createConnection(UUID owner) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "ownerId", owner,
                "conversationId", UUID.randomUUID(),
                "name", "sync-" + UUID.randomUUID(),
                "kind", "mysql",
                "config", Map.of(
                        "host", MYSQL.getHost(),
                        "port", MYSQL.getMappedPort(3306),
                        "database", "demo",
                        "username", "reader"
                ),
                "secret", Map.of("password", "reader")
        ));
        JsonNode created = json(mvc.perform(post("/internal/v1/data-connections")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return UUID.fromString(created.path("id").asText());
    }

    private String triggerBody(UUID operationId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "operationId", operationId,
                "tables", java.util.List.of(Map.of("schema", "demo", "table", "customers"))
        ));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
