package com.ontoweave.data.connector.mysql;

import com.ontoweave.data.connection.config.MysqlConfig;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class MysqlConnectionTester {

    private final MysqlJdbc jdbc;

    public MysqlConnectionTester(MysqlJdbc jdbc) {
        this.jdbc = jdbc;
    }

    public MysqlTestResult test(MysqlConfig config, String password) {
        long started = System.nanoTime();
        try (Connection connection = jdbc.open(config, password)) {
            String version = queryVersion(connection);
            Map<String, String> variables = queryVariables(connection);
            boolean masterStatus = canReadMasterStatus(connection, version);
            String grants = grants(connection);
            boolean replicationClient = grants.contains("REPLICATION CLIENT");
            boolean replicationSlave = grants.contains("REPLICATION SLAVE")
                    || grants.contains("REPLICATION REPLICA");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("serverVersion", version);
            details.put("binlog", Map.of(
                    "enabled", "ON".equalsIgnoreCase(variables.getOrDefault("log_bin", "OFF")),
                    "format", variables.getOrDefault("binlog_format", ""),
                    "rowImage", variables.getOrDefault("binlog_row_image", ""),
                    "expireLogsSeconds", parseLong(variables.get("binlog_expire_logs_seconds"))
            ));
            details.put("replication", Map.of(
                    "client", replicationClient,
                    "slave", replicationSlave,
                    "masterStatus", masterStatus
            ));
            return new MysqlTestResult(
                    true,
                    elapsedMillis(started),
                    version,
                    null,
                    "连接成功",
                    details
            );
        } catch (SQLException exception) {
            MysqlFailure failure = MysqlErrors.map(exception);
            return new MysqlTestResult(
                    false,
                    elapsedMillis(started),
                    null,
                    failure.errorCode(),
                    failure.message(),
                    Map.of("stage", "CONNECT")
            );
        }
    }

    private String queryVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT VERSION()")) {
            result.next();
            return result.getString(1);
        }
    }

    private Map<String, String> queryVariables(Connection connection) throws SQLException {
        Map<String, String> variables = new HashMap<>();
        String sql = "SHOW VARIABLES WHERE Variable_name IN "
                + "('log_bin','binlog_format','binlog_row_image','binlog_expire_logs_seconds')";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                variables.put(result.getString(1).toLowerCase(Locale.ROOT), result.getString(2));
            }
        }
        return variables;
    }

    private boolean canReadMasterStatus(Connection connection, String version) {
        String sql = version.startsWith("8.4") ? "SHOW BINARY LOG STATUS" : "SHOW MASTER STATUS";
        try (Statement statement = connection.createStatement();
             ResultSet ignored = statement.executeQuery(sql)) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    private String grants(Connection connection) {
        StringBuilder grants = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            while (result.next()) {
                grants.append(result.getString(1)).append(' ');
            }
        } catch (SQLException exception) {
            return "";
        }
        return grants.toString().toUpperCase(Locale.ROOT);
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}
