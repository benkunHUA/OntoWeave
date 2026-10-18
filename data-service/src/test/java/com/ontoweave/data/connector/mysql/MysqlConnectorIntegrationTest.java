package com.ontoweave.data.connector.mysql;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.config.MysqlConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MysqlConnectorIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("demo")
            .withUsername("ontoweave")
            .withPassword("ontoweave")
            .withCommand(
                    "--server-id=1",
                    "--log-bin=mysql-bin",
                    "--binlog-format=ROW",
                    "--binlog-row-image=FULL",
                    "--binlog-expire-logs-seconds=604800"
            );

    static MysqlConfig config;
    static MysqlJdbc jdbc;
    static MysqlConnectionTester tester;
    static MysqlTableDiscovery discovery;

    @BeforeAll
    static void prepare() throws Exception {
        config = new MysqlConfig(
                MYSQL.getHost(),
                MYSQL.getMappedPort(3306),
                MYSQL.getDatabaseName(),
                MYSQL.getUsername(),
                false
        );
        jdbc = new MysqlJdbc();
        tester = new MysqlConnectionTester(jdbc);
        discovery = new MysqlTableDiscovery(jdbc);
        try (Connection connection = jdbc.open(config, "ontoweave");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE customers (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(100) NOT NULL COMMENT '客户名',
                        amount DECIMAL(10,2) NULL
                    ) COMMENT='客户表'
                    """);
            statement.execute("CREATE TABLE no_pk (name VARCHAR(20))");
            statement.execute("INSERT INTO customers (id, name, amount) VALUES (1, 'alice', 12.50)");
        }
    }

    @Test
    void testsConnectionAndRecordsCapabilities() {
        MysqlTestResult result = tester.test(config, "ontoweave");

        assertThat(result.success()).isTrue();
        assertThat(result.serverVersion()).isNotBlank();
        assertThat(result.details()).containsKey("binlog");
        assertThat(result.errorCode()).isNull();
    }

    @Test
    void mapsWrongPasswordToAuthFailed() {
        MysqlTestResult result = tester.test(config, "wrong-password");

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("AUTH_FAILED");
    }

    @Test
    void listsTablesAndAggregatesPrimaryKeys() {
        var response = discovery.list(config, "ontoweave");

        assertThat(response.tables())
                .extracting("name")
                .contains("customers", "no_pk");
        assertThat(response.tables())
                .filteredOn(table -> table.name().equals("customers"))
                .singleElement()
                .satisfies(table -> assertThat(table.hasPrimaryKey()).isTrue());
        assertThat(response.tables())
                .filteredOn(table -> table.name().equals("no_pk"))
                .singleElement()
                .satisfies(table -> assertThat(table.hasPrimaryKey()).isFalse());
    }

    @Test
    void describesColumnsAndPrimaryKey() {
        var detail = discovery.describe(config, "ontoweave", MYSQL.getDatabaseName(), "customers");

        assertThat(detail.primaryKeyColumns()).containsExactly("id");
        assertThat(detail.columns()).extracting("name").containsExactly("id", "name", "amount");
        assertThat(detail.columns())
                .filteredOn(column -> column.name().equals("name"))
                .singleElement()
                .satisfies(column -> {
                    assertThat(column.nullable()).isFalse();
                    assertThat(column.comment()).isEqualTo("客户名");
                });
    }

    @Test
    void rejectsOtherSchemasAndMissingTables() {
        assertThatThrownBy(() -> discovery.describe(config, "ontoweave", "other", "customers"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("UNSUPPORTED_SCHEMA");

        assertThatThrownBy(() -> discovery.describe(
                config,
                "ontoweave",
                MYSQL.getDatabaseName(),
                "missing"
        )).isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("NOT_FOUND");
    }
}
