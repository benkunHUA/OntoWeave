package com.ontoweave.data.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.connection.config.MysqlConfig;
import com.ontoweave.data.connector.mysql.MysqlConnectionTester;
import com.ontoweave.data.connector.mysql.MysqlTestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DataConnectionApiIntegrationTest.StubMysqlTesterConfig.class)
class DataConnectionApiIntegrationTest {

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
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DELETE FROM data.outbox_event");
        jdbc.execute("DELETE FROM data.data_connection_secret");
        jdbc.execute("DELETE FROM data.data_connection");
    }

    @Test
    void createsReadsUpdatesAndTestsAConnection() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        String name = "mysql-" + UUID.randomUUID();

        JsonNode created = create(owner, conversation, name);
        UUID id = UUID.fromString(created.path("id").asText());
        assertThat(created.path("configVersion").asInt()).isEqualTo(1);
        assertThat(created.path("lastTestStatus").asText()).isEqualTo("untested");
        assertThat(created.path("secretConfigured").asBoolean()).isTrue();
        assertThat(created.path("createdAt").isNull()).isFalse();
        assertThat(created.path("updatedAt").isNull()).isFalse();
        assertThat(created.toString()).doesNotContain("p@ssword");

        JsonNode repeated = create(owner, conversation, name);
        assertThat(repeated.path("id").asText()).isEqualTo(id.toString());

        JsonNode tested = json(mvc.perform(post("/internal/v1/data-connections/{id}/test", id)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(tested.path("status").asText()).isEqualTo("success");

        JsonNode active = json(mvc.perform(get("/internal/v1/data-connections/{id}", id)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(active.path("status").asText()).isEqualTo("active");

        JsonNode renamed = json(mvc.perform(patch("/internal/v1/data-connections/{id}", id)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"renamed-%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(renamed.path("lastTestStatus").asText()).isEqualTo("success");
        assertThat(renamed.path("configVersion").asInt()).isEqualTo(1);

        JsonNode reconfigured = json(mvc.perform(patch("/internal/v1/data-connections/{id}", id)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"config":{"host":"db.internal","port":3307,"database":"demo","username":"reader"}}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(reconfigured.path("configVersion").asInt()).isEqualTo(2);
        assertThat(reconfigured.path("lastTestStatus").asText()).isEqualTo("untested");

        JsonNode page = json(mvc.perform(get("/internal/v1/data-connections")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(id.toString());

        JsonNode state = json(mvc.perform(get("/internal/v1/data-connections/{id}/state", id)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", owner.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(state.path("missingSteps").toString()).contains("TEST_CONNECTION", "SELECT_TABLES");
    }

    @Test
    void rejectsMissingTokenAndCrossOwnerAccess() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        JsonNode created = create(owner, conversation, "owned-" + UUID.randomUUID());
        UUID id = UUID.fromString(created.path("id").asText());

        mvc.perform(get("/internal/v1/data-connections/{id}", id))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/internal/v1/data-connections/{id}", id)
                        .header("X-Internal-Token", "test-internal-token")
                        .param("ownerId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/internal/v1/data-connections/{id}", id)
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/internal/v1/data-connections/{id}/state", id)
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode create(UUID owner, UUID conversation, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "ownerId", owner,
                "conversationId", conversation,
                "name", name,
                "kind", "mysql",
                "config", Map.of(
                        "host", "127.0.0.1",
                        "port", 3306,
                        "database", "demo",
                        "username", "reader"
                ),
                "secret", Map.of("password", "p@ssword")
        ));
        return json(mvc.perform(post("/internal/v1/data-connections")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    @TestConfiguration
    static class StubMysqlTesterConfig {

        @Bean
        @Primary
        MysqlConnectionTester mysqlConnectionTester() {
            return new MysqlConnectionTester(null) {
                @Override
                public MysqlTestResult test(MysqlConfig config, String password) {
                    return new MysqlTestResult(
                            true,
                            12,
                            "8.0.36",
                            null,
                            "连接成功",
                            Map.of("serverVersion", "8.0.36")
                    );
                }
            };
        }
    }
}
