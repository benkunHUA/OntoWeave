package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.config.SyncProperties;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.table.api.ApiExpression;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.api.GroupedTable;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.catalog.Catalog;
import org.apache.flink.table.expressions.Expression;
import org.apache.flink.table.types.DataType;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.iceberg.flink.FlinkCatalogFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PipelinePlanCompiler {

    private static final String HIDDEN_PREFIX = "__ontoweave_preview_";
    private static final String JOIN_ALIAS_LEFT = "l";
    private static final String JOIN_ALIAS_RIGHT = "r";

    private final String warehouse;
    private final FlinkExpressionCompiler expressions = new FlinkExpressionCompiler();

    public PipelinePlanCompiler(SyncProperties properties) {
        this.warehouse = properties.icebergWarehouse();
    }

    public CompiledTablePlan compile(LogicalPlan plan) {
        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.setRuntimeMode(RuntimeExecutionMode.BATCH);
        StreamTableEnvironment tableEnvironment = StreamTableEnvironment.create(environment);
        CompilerState state = new CompilerState(tableEnvironment);
        Table table = compileStep(plan.root(), state);
        List<CompiledTablePlan.HiddenErrorColumn> hidden = state.hiddenColumns.stream()
                .filter(column -> table.getResolvedSchema().getColumnNames().contains(column.flagColumn()))
                .toList();
        return new CompiledTablePlan(tableEnvironment, table, hidden);
    }

    private Table compileStep(PlanStep step, CompilerState state) {
        LogicalPlanNode node = step.node();
        if (node instanceof LogicalPlanNode.ScanInput scan) {
            return scan(scan, state);
        }
        if (node instanceof LogicalPlanNode.Filter filter) {
            return compileStep(step.inputs().get(0), state).filter(expressions.condition(filter.condition()));
        }
        if (node instanceof LogicalPlanNode.SelectColumns select) {
            return select(compileStep(step.inputs().get(0), state), select);
        }
        if (node instanceof LogicalPlanNode.RenameColumns rename) {
            return rename(compileStep(step.inputs().get(0), state), rename);
        }
        if (node instanceof LogicalPlanNode.DeriveColumn derive) {
            return derive(step, compileStep(step.inputs().get(0), state), derive, state);
        }
        if (node instanceof LogicalPlanNode.Deduplicate deduplicate) {
            return deduplicate(compileStep(step.inputs().get(0), state), deduplicate);
        }
        if (node instanceof LogicalPlanNode.Join join) {
            return join(
                    step,
                    compileStep(step.inputs().get(0), state),
                    compileStep(step.inputs().get(1), state),
                    join
            );
        }
        if (node instanceof LogicalPlanNode.Union) {
            return union(step, state);
        }
        if (node instanceof LogicalPlanNode.Aggregate aggregate) {
            return aggregate(compileStep(step.inputs().get(0), state), aggregate);
        }
        throw new IllegalArgumentException("Unsupported logical plan node: " + node);
    }

    private Table scan(LogicalPlanNode.ScanInput scan, CompilerState state) {
        registerCatalog(scan, state.tableEnvironment);
        TableDescriptor descriptor = TableDescriptor.forConnector("iceberg")
                .option("catalog-name", scan.catalog())
                .option(FlinkCatalogFactory.ICEBERG_CATALOG_TYPE, FlinkCatalogFactory.ICEBERG_CATALOG_TYPE_HADOOP)
                .option("catalog-database", scan.namespace())
                .option("catalog-table", scan.table())
                .option("warehouse", warehouse)
                .option("snapshot-id", Long.toString(scan.snapshotId()))
                .option("limit", Integer.toString(scan.scanLimit()))
                .build();
        return state.tableEnvironment.from(descriptor);
    }

    private void registerCatalog(LogicalPlanNode.ScanInput scan, StreamTableEnvironment tableEnvironment) {
        if (stateCatalogs(tableEnvironment).contains(scan.catalog())) {
            return;
        }
        Map<String, String> properties = new HashMap<>();
        properties.put("type", "iceberg");
        properties.put(FlinkCatalogFactory.ICEBERG_CATALOG_TYPE, FlinkCatalogFactory.ICEBERG_CATALOG_TYPE_HADOOP);
        properties.put("warehouse", warehouse);
        Catalog catalog = new FlinkCatalogFactory().createCatalog(scan.catalog(), properties);
        tableEnvironment.registerCatalog(scan.catalog(), catalog);
    }

    private Set<String> stateCatalogs(StreamTableEnvironment tableEnvironment) {
        return Set.of(tableEnvironment.listCatalogs());
    }

    private Table select(Table input, LogicalPlanNode.SelectColumns select) {
        List<String> schemaColumns = input.getResolvedSchema().getColumnNames();
        List<String> hidden = hiddenColumns(schemaColumns);
        Set<String> selected = new LinkedHashSet<>(select.columns());
        List<String> columns = new ArrayList<>();
        if ("exclude".equals(select.mode())) {
            for (String column : schemaColumns) {
                if (hidden.contains(column) || !selected.contains(column)) {
                    columns.add(column);
                }
            }
        } else if (selected.isEmpty()) {
            columns.addAll(schemaColumns);
        } else {
            for (String column : schemaColumns) {
                if (selected.contains(column) || hidden.contains(column)) {
                    columns.add(column);
                }
            }
        }
        return input.select(columns.stream().map(Expressions::$).toArray(Expression[]::new));
    }

    private Table rename(Table input, LogicalPlanNode.RenameColumns rename) {
        Expression[] renames = rename.renames().stream()
                .map(column -> Expressions.$(column.from()).as(column.to()))
                .toArray(Expression[]::new);
        return renames.length == 0 ? input : input.renameColumns(renames);
    }

    private Table derive(
            PlanStep step,
            Table input,
            LogicalPlanNode.DeriveColumn derive,
            CompilerState state
    ) {
        JsonNode expressionNode = derive.expression();
        FlinkExpressionCompiler.CompiledExpression compiled = expressions.compile(expressionNode);
        ApiExpression expression = compiled.expression();
        if (compiled.failures().isEmpty()) {
            return input.addColumns(expression.as(derive.outputField()));
        }
        List<ApiExpression> columns = new ArrayList<>();
        columns.add(expression.as(derive.outputField()));
        for (FlinkExpressionCompiler.CastFailure failure : compiled.failures()) {
            String flagColumn = HIDDEN_PREFIX + state.hiddenColumns.size() + "_flag";
            String originalColumn = HIDDEN_PREFIX + state.hiddenColumns.size() + "_original";
            state.hiddenColumns.add(new CompiledTablePlan.HiddenErrorColumn(
                    step.nodeId(), derive.outputField(), flagColumn, originalColumn
            ));
            columns.add(failure.flag().as(flagColumn));
            columns.add(failure.original().as(originalColumn));
        }
        return input.addColumns(columns.toArray(ApiExpression[]::new));
    }

    private Table deduplicate(Table input, LogicalPlanNode.Deduplicate deduplicate) {
        if (!"subset".equals(deduplicate.mode()) || deduplicate.columns().isEmpty()) {
            return input.distinct();
        }
        GroupedTable grouped = input.groupBy(deduplicate.columns().stream()
                .map(Expressions::$)
                .toArray(Expression[]::new));
        List<Expression> selected = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>(deduplicate.columns());
        for (String column : input.getResolvedSchema().getColumnNames()) {
            selected.add(keys.contains(column)
                    ? Expressions.$(column)
                    : Expressions.$(column).firstValue().as(column));
        }
        return grouped.select(selected.toArray(Expression[]::new));
    }

    private Table join(PlanStep step, Table left, Table right, LogicalPlanNode.Join join) {
        Table aliasedLeft = left.as(JOIN_ALIAS_LEFT);
        Table aliasedRight = right.as(JOIN_ALIAS_RIGHT);
        ApiExpression condition = joinCondition(join);
        Table joined = switch (join.joinType()) {
            case "left" -> aliasedLeft.leftOuterJoin(aliasedRight, condition);
            case "right" -> aliasedLeft.rightOuterJoin(aliasedRight, condition);
            case "full" -> aliasedLeft.fullOuterJoin(aliasedRight, condition);
            default -> aliasedLeft.join(aliasedRight, condition);
        };

        List<Expression> selected = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (String column : aliasedLeft.getResolvedSchema().getColumnNames()) {
            names.add(column);
            selected.add(Expressions.$(JOIN_ALIAS_LEFT + "." + column).as(column));
        }
        for (String column : aliasedRight.getResolvedSchema().getColumnNames()) {
            String outputName = uniqueJoinName(column, names);
            names.add(outputName);
            selected.add(Expressions.$(JOIN_ALIAS_RIGHT + "." + column).as(outputName));
        }
        return joined.select(selected.toArray(Expression[]::new));
    }

    private ApiExpression joinCondition(LogicalPlanNode.Join join) {
        ApiExpression condition = null;
        for (LogicalPlanNode.JoinKey key : join.keys()) {
            ApiExpression current = Expressions.$(JOIN_ALIAS_LEFT + "." + key.left())
                    .isEqual(Expressions.$(JOIN_ALIAS_RIGHT + "." + key.right()));
            condition = condition == null ? current : condition.and(current);
        }
        if (condition == null) {
            throw new IllegalArgumentException("Join requires at least one key");
        }
        return condition;
    }

    private String uniqueJoinName(String name, Set<String> names) {
        if (!names.contains(name)) {
            return name;
        }
        String base = "right_" + name;
        String candidate = base;
        int suffix = 2;
        while (names.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private Table union(PlanStep step, CompilerState state) {
        Table result = compileStep(step.inputs().get(0), state);
        for (int index = 1; index < step.inputs().size(); index++) {
            result = result.unionAll(compileStep(step.inputs().get(index), state));
        }
        return result;
    }

    private Table aggregate(Table input, LogicalPlanNode.Aggregate aggregate) {
        List<Expression> selected = new ArrayList<>();
        for (String field : aggregate.groupBy()) {
            selected.add(Expressions.$(field));
        }
        for (LogicalPlanNode.Aggregation aggregation : aggregate.aggregations()) {
            selected.add(aggregationExpression(aggregation));
        }
        if (aggregate.groupBy().isEmpty()) {
            return input.select(selected.toArray(Expression[]::new));
        }
        GroupedTable grouped = input.groupBy(aggregate.groupBy().stream()
                .map(Expressions::$)
                .toArray(Expression[]::new));
        return grouped.select(selected.toArray(Expression[]::new));
    }

    private Expression aggregationExpression(LogicalPlanNode.Aggregation aggregation) {
        ApiExpression source = aggregation.field() == null || aggregation.field().isBlank()
                ? Expressions.lit(1)
                : Expressions.$(aggregation.field());
        ApiExpression aggregated = switch (aggregation.op()) {
            case "count" -> source.count();
            case "sum" -> source.sum();
            case "avg" -> source.avg();
            case "min" -> source.min();
            case "max" -> source.max();
            default -> throw new IllegalArgumentException("Unsupported aggregation: " + aggregation.op());
        };
        return aggregated.as(aggregation.outputField());
    }

    private List<String> hiddenColumns(List<String> schemaColumns) {
        return schemaColumns.stream().filter(column -> column.startsWith(HIDDEN_PREFIX)).toList();
    }

    private static final class CompilerState {

        private final StreamTableEnvironment tableEnvironment;
        private final List<CompiledTablePlan.HiddenErrorColumn> hiddenColumns = new ArrayList<>();

        private CompilerState(StreamTableEnvironment tableEnvironment) {
            this.tableEnvironment = tableEnvironment;
        }
    }
}
