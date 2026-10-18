package com.ontoweave.data.connector.mysql;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.api.dto.ColumnResponse;
import com.ontoweave.data.connection.api.dto.TableDetailResponse;
import com.ontoweave.data.connection.api.dto.TableListResponse;
import com.ontoweave.data.connection.api.dto.TableSummaryResponse;
import com.ontoweave.data.connection.config.MysqlConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class MysqlTableDiscovery {

    private static final String TABLE_QUERY = """
            SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, TABLE_COMMENT, TABLE_ROWS
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_NAME
            """;
    private static final String PRIMARY_KEY_QUERY = """
            SELECT DISTINCT TABLE_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = ? AND INDEX_NAME = 'PRIMARY'
            """;
    private static final String COLUMN_QUERY = """
            SELECT COLUMN_NAME, ORDINAL_POSITION, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE,
                   COLUMN_DEFAULT, COLUMN_KEY, COLUMN_COMMENT
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;

    private final MysqlJdbc jdbc;

    public MysqlTableDiscovery(MysqlJdbc jdbc) {
        this.jdbc = jdbc;
    }

    public TableListResponse list(MysqlConfig config, String password) {
        try (Connection connection = jdbc.open(config, password)) {
            Set<String> primaryKeys = primaryKeyTables(connection, config.database());
            List<TableSummaryResponse> tables = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(TABLE_QUERY)) {
                statement.setString(1, config.database());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String table = result.getString("TABLE_NAME");
                        long rows = result.getLong("TABLE_ROWS");
                        Long estimatedRows = result.wasNull() ? null : rows;
                        tables.add(new TableSummaryResponse(
                                result.getString("TABLE_SCHEMA"),
                                table,
                                result.getString("TABLE_TYPE"),
                                result.getString("TABLE_COMMENT"),
                                estimatedRows,
                                primaryKeys.contains(table)
                        ));
                    }
                }
            }
            List<String> warnings = tables.isEmpty() ? List.of("NO_VISIBLE_TABLES") : List.of();
            return new TableListResponse(tables, warnings);
        } catch (SQLException exception) {
            throw sourceError(exception);
        }
    }

    public TableDetailResponse describe(MysqlConfig config, String password, String schema, String table) {
        if (!config.database().equals(schema)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_SCHEMA",
                    "只能查看连接配置中指定的 database"
            );
        }
        try (Connection connection = jdbc.open(config, password)) {
            TableMeta meta = tableMeta(connection, schema, table);
            if (meta == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "表不存在");
            }
            if (!"BASE TABLE".equalsIgnoreCase(meta.type())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TABLE_TYPE", "只支持普通表");
            }
            List<ColumnResponse> columns = columns(connection, schema, table);
            List<String> primaryKeys = columns.stream()
                    .filter(ColumnResponse::primaryKey)
                    .map(ColumnResponse::name)
                    .toList();
            return new TableDetailResponse(schema, table, meta.type(), meta.comment(), columns, primaryKeys);
        } catch (SQLException exception) {
            throw sourceError(exception);
        }
    }

    public long countRows(MysqlConfig config, String password, String schema, String table) {
        if (!config.database().equals(schema)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_SCHEMA",
                    "只能统计连接配置中指定的 database"
            );
        }
        String sql = "SELECT COUNT(*) FROM " + quote(schema) + "." + quote(table);
        try (Connection connection = jdbc.open(config, password);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        } catch (SQLException exception) {
            throw sourceError(exception);
        }
    }

    public String serverTimeZone(MysqlConfig config, String password) {
        try (Connection connection = jdbc.open(config, password);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW())");
             ResultSet result = statement.executeQuery()) {
            long offsetSeconds = result.next() ? result.getLong(1) : 0L;
            char sign = offsetSeconds < 0 ? '-' : '+';
            long absolute = Math.abs(offsetSeconds);
            return String.format("%c%02d:%02d", sign, absolute / 3600, (absolute % 3600) / 60);
        } catch (SQLException exception) {
            throw sourceError(exception);
        }
    }

    private Set<String> primaryKeyTables(Connection connection, String schema) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(PRIMARY_KEY_QUERY)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }
        }
        return tables;
    }

    private TableMeta tableMeta(Connection connection, String schema, String table) throws SQLException {
        String sql = """
                SELECT TABLE_TYPE, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new TableMeta(result.getString("TABLE_TYPE"), result.getString("TABLE_COMMENT"));
            }
        }
    }

    private List<ColumnResponse> columns(Connection connection, String schema, String table) throws SQLException {
        List<ColumnResponse> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(COLUMN_QUERY)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(new ColumnResponse(
                            result.getString("COLUMN_NAME"),
                            result.getInt("ORDINAL_POSITION"),
                            result.getString("DATA_TYPE"),
                            result.getString("COLUMN_TYPE"),
                            "YES".equalsIgnoreCase(result.getString("IS_NULLABLE")),
                            result.getString("COLUMN_DEFAULT"),
                            "PRI".equalsIgnoreCase(result.getString("COLUMN_KEY")),
                            result.getString("COLUMN_COMMENT")
                    ));
                }
            }
        }
        return columns;
    }

    private ApiException sourceError(SQLException exception) {
        MysqlFailure failure = MysqlErrors.map(exception);
        return new ApiException(HttpStatus.BAD_GATEWAY, failure.errorCode(), failure.message());
    }

    private String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private record TableMeta(String type, String comment) {
    }
}
