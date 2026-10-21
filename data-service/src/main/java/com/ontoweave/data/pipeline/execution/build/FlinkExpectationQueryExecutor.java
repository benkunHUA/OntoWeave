package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.execution.output.OutputSnapshot;
import com.ontoweave.data.pipeline.execution.plan.CompiledTablePlan;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FlinkExpectationQueryExecutor implements ExpectationQueryExecutor {

    private static final String VIEW = "ontoweave_expectation_snapshot";

    private final PipelinePlanCompiler compiler;

    public FlinkExpectationQueryExecutor(PipelinePlanCompiler compiler) {
        this.compiler = compiler;
    }

    @Override
    public PrimaryKeyStats primaryKey(OutputSnapshot snapshot, List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return new PrimaryKeyStats(0L, 0L);
        }
        long nullRows = scalar(snapshot, "SELECT COUNT(*) FROM " + VIEW
                + " WHERE " + anyNull(columns));
        long duplicateRows = scalar(snapshot, "SELECT COALESCE(SUM(cnt - 1), 0) FROM ("
                + "SELECT " + identifiers(columns) + ", COUNT(*) AS cnt FROM " + VIEW
                + " WHERE " + allNotNull(columns)
                + " GROUP BY " + identifiers(columns)
                + " HAVING COUNT(*) > 1) AS duplicates");
        return new PrimaryKeyStats(nullRows, duplicateRows);
    }

    @Override
    public NullStats notNull(OutputSnapshot snapshot, List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return new NullStats(0L, Map.of());
        }
        List<String> select = new ArrayList<>();
        Map<String, Long> nullRowsByColumn = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            String column = columns.get(index);
            select.add("COALESCE(SUM(CASE WHEN " + identifier(column)
                    + " IS NULL THEN 1 ELSE 0 END), 0) AS nulls_" + index);
            nullRowsByColumn.put(column, 0L);
        }
        select.add("COALESCE(SUM(CASE WHEN " + anyNull(columns) + " THEN 1 ELSE 0 END), 0) AS failed_rows");
        Row row = firstRow(snapshot, "SELECT " + String.join(", ", select) + " FROM " + VIEW);
        for (int index = 0; index < columns.size(); index++) {
            nullRowsByColumn.put(columns.get(index), longValue(row.getField(index)));
        }
        return new NullStats(longValue(row.getField(columns.size())), nullRowsByColumn);
    }

    @Override
    public long invalidValueRows(OutputSnapshot snapshot, String column, List<String> allowedValues) {
        if (column == null || allowedValues == null || allowedValues.isEmpty()) {
            return 0L;
        }
        String allowed = allowedValues.stream()
                .map(this::literal)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        String condition = identifier(column) + " IS NULL OR CAST(" + identifier(column)
                + " AS STRING) NOT IN (" + allowed + ")";
        return scalar(snapshot,
                "SELECT COALESCE(SUM(CASE WHEN " + condition + " THEN 1 ELSE 0 END), 0) FROM " + VIEW);
    }

    private long scalar(OutputSnapshot snapshot, String sql) {
        return longValue(firstRow(snapshot, sql).getField(0));
    }

    private Row firstRow(OutputSnapshot snapshot, String sql) {
        CompiledTablePlan scan = compiler.snapshotScan(
                snapshot.icebergCatalog(),
                snapshot.icebergNamespace(),
                snapshot.icebergTable(),
                snapshot.snapshotId()
        );
        StreamTableEnvironment tableEnvironment = scan.tableEnvironment();
        tableEnvironment.createTemporaryView(VIEW, scan.table());
        try (CloseableIterator<Row> rows = tableEnvironment.sqlQuery(sql).execute().collect()) {
            return rows.hasNext() ? rows.next() : Row.of();
        } catch (Exception error) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_EXECUTION_FAILED,
                    "数据期望查询失败",
                    error
            );
        }
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String identifiers(List<String> columns) {
        return columns.stream().map(this::identifier).reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String anyNull(List<String> columns) {
        return columns.stream().map(column -> identifier(column) + " IS NULL")
                .reduce((left, right) -> left + " OR " + right).orElse("FALSE");
    }

    private String allNotNull(List<String> columns) {
        return columns.stream().map(column -> identifier(column) + " IS NOT NULL")
                .reduce((left, right) -> left + " AND " + right).orElse("TRUE");
    }

    private String identifier(String column) {
        return "`" + column.replace("`", "``") + "`";
    }

    private String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
