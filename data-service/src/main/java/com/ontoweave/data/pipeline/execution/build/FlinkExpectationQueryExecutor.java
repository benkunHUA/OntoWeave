package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.execution.output.OutputSnapshot;
import com.ontoweave.data.pipeline.execution.plan.CompiledTablePlan;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FlinkExpectationQueryExecutor implements ExpectationQueryExecutor {

    private final PipelinePlanCompiler compiler;

    public FlinkExpectationQueryExecutor(PipelinePlanCompiler compiler) {
        this.compiler = compiler;
    }

    @Override
    public PrimaryKeyStats primaryKey(OutputSnapshot snapshot, List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return new PrimaryKeyStats(0L, 0L);
        }
        long nullRows = scalar(snapshot, ExpectationSqlBuilder.primaryKeyNullRows(columns));
        long duplicateRows = scalar(snapshot, ExpectationSqlBuilder.primaryKeyDuplicateRows(columns));
        return new PrimaryKeyStats(nullRows, duplicateRows);
    }

    @Override
    public NullStats notNull(OutputSnapshot snapshot, List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return new NullStats(0L, Map.of());
        }
        Map<String, Long> nullRowsByColumn = new LinkedHashMap<>();
        columns.forEach(column -> nullRowsByColumn.put(column, 0L));
        Row row = firstRow(snapshot, ExpectationSqlBuilder.notNullRows(columns));
        for (int index = 0; index < columns.size(); index++) {
            String column = columns.get(index);
            nullRowsByColumn.put(column, longValue(row.getField(index)));
        }
        return new NullStats(longValue(row.getField(columns.size())), nullRowsByColumn);
    }

    @Override
    public long invalidValueRows(OutputSnapshot snapshot, String column, List<String> allowedValues) {
        if (column == null || allowedValues == null || allowedValues.isEmpty()) {
            return 0L;
        }
        return scalar(snapshot, ExpectationSqlBuilder.invalidValueRows(column, allowedValues));
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
        tableEnvironment.createTemporaryView(ExpectationSqlBuilder.VIEW, scan.table());
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
}
