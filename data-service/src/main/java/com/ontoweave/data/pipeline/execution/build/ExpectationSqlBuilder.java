package com.ontoweave.data.pipeline.execution.build;

import java.util.ArrayList;
import java.util.List;

public final class ExpectationSqlBuilder {

    public static final String VIEW = "ontoweave_expectation_snapshot";

    private ExpectationSqlBuilder() {
    }

    public static String primaryKeyNullRows(List<String> columns) {
        requireColumns(columns);
        return "SELECT COUNT(*) FROM " + VIEW + " WHERE " + anyNull(columns);
    }

    public static String primaryKeyDuplicateRows(List<String> columns) {
        requireColumns(columns);
        String identifiers = identifiers(columns);
        return "SELECT COALESCE(SUM(cnt), 0) FROM ("
                + "SELECT " + identifiers + ", COUNT(*) AS cnt FROM " + VIEW
                + " WHERE " + allNotNull(columns)
                + " GROUP BY " + identifiers
                + " HAVING COUNT(*) > 1) AS duplicates";
    }

    public static String notNullRows(List<String> columns) {
        requireColumns(columns);
        List<String> select = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            select.add("COALESCE(SUM(CASE WHEN " + identifier(columns.get(index))
                    + " IS NULL THEN 1 ELSE 0 END), 0) AS nulls_" + index);
        }
        select.add("COALESCE(SUM(CASE WHEN " + anyNull(columns)
                + " THEN 1 ELSE 0 END), 0) AS failed_rows");
        return "SELECT " + String.join(", ", select) + " FROM " + VIEW;
    }

    public static String invalidValueRows(String column, List<String> allowedValues) {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("column is required");
        }
        if (allowedValues == null || allowedValues.isEmpty()) {
            throw new IllegalArgumentException("allowed values are required");
        }
        String allowed = allowedValues.stream()
                .map(ExpectationSqlBuilder::literal)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        String condition = identifier(column) + " IS NULL OR CAST(" + identifier(column)
                + " AS STRING) NOT IN (" + allowed + ")";
        return "SELECT COALESCE(SUM(CASE WHEN " + condition
                + " THEN 1 ELSE 0 END), 0) FROM " + VIEW;
    }

    private static void requireColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns are required");
        }
    }

    private static String identifiers(List<String> columns) {
        return String.join(", ", columns.stream().map(ExpectationSqlBuilder::identifier).toList());
    }

    private static String anyNull(List<String> columns) {
        return String.join(" OR ", columns.stream()
                .map(column -> identifier(column) + " IS NULL")
                .toList());
    }

    private static String allNotNull(List<String> columns) {
        return String.join(" AND ", columns.stream()
                .map(column -> identifier(column) + " IS NOT NULL")
                .toList());
    }

    private static String identifier(String column) {
        return "`" + column.replace("`", "``") + "`";
    }

    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
