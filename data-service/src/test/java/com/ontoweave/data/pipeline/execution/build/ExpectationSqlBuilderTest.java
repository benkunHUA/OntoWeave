package com.ontoweave.data.pipeline.execution.build;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.SecurityOptions;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectationSqlBuilderTest {

    @Test
    void countsEveryRowInDuplicatePrimaryKeyGroups() {
        TableEnvironment environment = environment();
        environment.createTemporaryView(
                ExpectationSqlBuilder.VIEW,
                environment.fromValues(
                        DataTypes.ROW(
                                DataTypes.FIELD("id", DataTypes.BIGINT()),
                                DataTypes.FIELD("status", DataTypes.STRING())
                        ),
                        Row.of(1L, "active"),
                        Row.of(1L, "active"),
                        Row.of(1L, "closed"),
                        Row.of(2L, "active"),
                        Row.of(2L, "active"),
                        Row.of(3L, "active"),
                        Row.of(null, "active")
                )
        );

        String duplicates = ExpectationSqlBuilder.primaryKeyDuplicateRows(List.of("id"));

        assertThat(duplicates).contains("SUM(cnt)").doesNotContain("cnt - 1");
        assertThat(scalar(environment, duplicates)).isEqualTo(5L);
        assertThat(scalar(
                environment,
                ExpectationSqlBuilder.primaryKeyNullRows(List.of("id"))
        )).isEqualTo(1L);
    }

    @Test
    void buildsNotNullAggregateAndEscapesBackticksInColumnNames() {
        String escapedColumn = "status`code";
        TableEnvironment environment = environment();
        environment.createTemporaryView(
                ExpectationSqlBuilder.VIEW,
                environment.fromValues(
                        DataTypes.ROW(
                                DataTypes.FIELD(escapedColumn, DataTypes.STRING()),
                                DataTypes.FIELD("name", DataTypes.STRING())
                        ),
                        Row.of("active", "one"),
                        Row.of(null, "two"),
                        Row.of("closed", null),
                        Row.of(null, null)
                )
        );

        String sql = ExpectationSqlBuilder.notNullRows(List.of(escapedColumn, "name"));

        assertThat(sql).contains("`status``code`");
        Row row = firstRow(environment, sql);
        assertThat(((Number) row.getField(0)).longValue()).isEqualTo(2L);
        assertThat(((Number) row.getField(1)).longValue()).isEqualTo(2L);
        assertThat(((Number) row.getField(2)).longValue()).isEqualTo(3L);
    }

    @Test
    void buildsValueSetQueryAndEscapesSingleQuotesInValues() {
        String escapedColumn = "status`code";
        TableEnvironment environment = environment();
        environment.createTemporaryView(
                ExpectationSqlBuilder.VIEW,
                environment.fromValues(
                        DataTypes.ROW(
                                DataTypes.FIELD(escapedColumn, DataTypes.STRING())
                        ),
                        Row.of("O'Brien"),
                        Row.of("O'Brien"),
                        Row.of("other"),
                        Row.of((Object) null)
                )
        );

        String sql = ExpectationSqlBuilder.invalidValueRows(escapedColumn, List.of("O'Brien"));

        assertThat(sql).contains("`status``code`").contains("'O''Brien'");
        assertThat(scalar(environment, sql)).isEqualTo(2L);
    }

    private TableEnvironment environment() {
        Configuration configuration = EnvironmentSettings.inBatchMode().toConfiguration();
        configuration.set(SecurityOptions.DELEGATION_TOKENS_ENABLED, false);
        return TableEnvironment.create(EnvironmentSettings.fromConfiguration(configuration));
    }

    private long scalar(TableEnvironment environment, String sql) {
        return ((Number) firstRow(environment, sql).getField(0)).longValue();
    }

    private Row firstRow(TableEnvironment environment, String sql) {
        try (CloseableIterator<Row> rows = environment.sqlQuery(sql).execute().collect()) {
            return rows.next();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
