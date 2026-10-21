package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.execution.flink.PipelineExecutionException;
import org.apache.flink.table.api.ApiExpression;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.api.GroupedTable;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.expressions.Expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class PlanStepCompiler {

    private static final String JOIN_ALIAS_LEFT = "l";
    private static final String JOIN_ALIAS_RIGHT = "r";

    private final FlinkExpressionCompiler expressions = new FlinkExpressionCompiler();
    private final Function<LogicalPlanNode.ScanInput, Table> scanSource;
    private int hiddenSequence;

    PlanStepCompiler(Function<LogicalPlanNode.ScanInput, Table> scanSource) {
        this.scanSource = scanSource;
    }

    CompiledStep compile(PlanStep root) {
        return compileStep(root);
    }

    private CompiledStep compileStep(PlanStep step) {
        try {
            return compileNode(step);
        } catch (PipelineExecutionException error) {
            throw error;
        } catch (RuntimeException error) {
            throw PipelineExecutionException.at(step, error);
        }
    }

    private CompiledStep compileNode(PlanStep step) {
        LogicalPlanNode node = step.node();
        if (node instanceof LogicalPlanNode.ScanInput scan) {
            return new CompiledStep(scanSource.apply(scan), List.of());
        }
        if (node instanceof LogicalPlanNode.Filter filter) {
            CompiledStep input = compileStep(step.inputs().get(0));
            return new CompiledStep(
                    input.table().filter(expressions.condition(filter.condition())),
                    input.hiddenColumns()
            );
        }
        if (node instanceof LogicalPlanNode.SelectColumns select) {
            return select(compileStep(step.inputs().get(0)), select);
        }
        if (node instanceof LogicalPlanNode.RenameColumns rename) {
            return rename(compileStep(step.inputs().get(0)), rename);
        }
        if (node instanceof LogicalPlanNode.DeriveColumn derive) {
            return derive(step, compileStep(step.inputs().get(0)), derive);
        }
        if (node instanceof LogicalPlanNode.Deduplicate deduplicate) {
            return deduplicate(compileStep(step.inputs().get(0)), deduplicate);
        }
        if (node instanceof LogicalPlanNode.Join join) {
            return join(
                    compileStep(step.inputs().get(0)),
                    compileStep(step.inputs().get(1)),
                    join
            );
        }
        if (node instanceof LogicalPlanNode.Union) {
            return union(step);
        }
        if (node instanceof LogicalPlanNode.Aggregate aggregate) {
            return aggregate(compileStep(step.inputs().get(0)), aggregate);
        }
        throw new IllegalArgumentException("Unsupported logical plan node: " + node);
    }

    private CompiledStep select(CompiledStep input, LogicalPlanNode.SelectColumns select) {
        List<String> schemaColumns = input.table().getResolvedSchema().getColumnNames();
        Set<String> selected = new LinkedHashSet<>(select.columns());
        Set<String> hidden = HiddenColumns.names(schemaColumns);
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
        return new CompiledStep(
                input.table().select(columns.stream().map(Expressions::$).toArray(Expression[]::new)),
                input.hiddenColumns()
        );
    }

    private CompiledStep rename(CompiledStep input, LogicalPlanNode.RenameColumns rename) {
        Expression[] renames = rename.renames().stream()
                .map(column -> Expressions.$(column.from()).as(column.to()))
                .toArray(Expression[]::new);
        Table renamed = renames.length == 0 ? input.table() : input.table().renameColumns(renames);
        Map<String, String> fields = new HashMap<>();
        rename.renames().forEach(column -> fields.put(column.from(), column.to()));
        List<CompiledTablePlan.HiddenErrorColumn> hidden = input.hiddenColumns().stream()
                .map(column -> column.field() != null && fields.containsKey(column.field())
                        ? column.withField(fields.get(column.field())) : column)
                .toList();
        return new CompiledStep(renamed, hidden);
    }

    private CompiledStep derive(PlanStep step, CompiledStep input, LogicalPlanNode.DeriveColumn derive) {
        JsonNode expressionNode = derive.expression();
        FlinkExpressionCompiler.CompiledExpression compiled = expressions.compile(expressionNode);
        List<ApiExpression> columns = new ArrayList<>();
        columns.add(compiled.expression().as(derive.outputField()));
        List<CompiledTablePlan.HiddenErrorColumn> hidden = new ArrayList<>(input.hiddenColumns());
        for (FlinkExpressionCompiler.CastFailure failure : compiled.failures()) {
            CompiledTablePlan.HiddenErrorColumn descriptor = hiddenColumn(step, derive.outputField());
            hidden.add(descriptor);
            columns.add(failure.flag().as(descriptor.flagColumn()));
            columns.add(failure.original().as(descriptor.originalColumn()));
        }
        return new CompiledStep(
                input.table().addColumns(columns.toArray(ApiExpression[]::new)), hidden
        );
    }

    private CompiledStep deduplicate(CompiledStep input, LogicalPlanNode.Deduplicate deduplicate) {
        if (!"subset".equals(deduplicate.mode()) || deduplicate.columns().isEmpty()) {
            return new CompiledStep(input.table().distinct(), input.hiddenColumns());
        }
        GroupedTable grouped = input.table().groupBy(deduplicate.columns().stream()
                .map(Expressions::$)
                .toArray(Expression[]::new));
        List<Expression> selected = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>(deduplicate.columns());
        Set<String> flags = input.hiddenColumns().stream()
                .map(CompiledTablePlan.HiddenErrorColumn::flagColumn)
                .collect(java.util.stream.Collectors.toSet());
        for (String column : input.table().getResolvedSchema().getColumnNames()) {
            if (keys.contains(column)) {
                selected.add(Expressions.$(column));
            } else if (flags.contains(column)) {
                selected.add(HiddenColumns.flagAggregate(column));
            } else {
                selected.add(Expressions.$(column).firstValue().as(column));
            }
        }
        return new CompiledStep(
                grouped.select(selected.toArray(Expression[]::new)), input.hiddenColumns()
        );
    }

    private CompiledStep join(CompiledStep left, CompiledStep right, LogicalPlanNode.Join join) {
        Table aliasedLeft = left.table().as(JOIN_ALIAS_LEFT);
        Table aliasedRight = right.table().as(JOIN_ALIAS_RIGHT);
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
        Map<String, String> rightNames = new LinkedHashMap<>();
        for (String column : aliasedRight.getResolvedSchema().getColumnNames()) {
            String outputName = HiddenColumns.uniqueJoinName(column, names);
            names.add(outputName);
            rightNames.put(column, outputName);
            selected.add(Expressions.$(JOIN_ALIAS_RIGHT + "." + column).as(outputName));
        }
        List<CompiledTablePlan.HiddenErrorColumn> hidden = new ArrayList<>(left.hiddenColumns());
        for (CompiledTablePlan.HiddenErrorColumn column : right.hiddenColumns()) {
            hidden.add(column.withNames(
                    rightNames.get(column.flagColumn()),
                    rightNames.get(column.originalColumn())
            ));
        }
        return new CompiledStep(joined.select(selected.toArray(Expression[]::new)), hidden);
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

    private CompiledStep union(PlanStep step) {
        List<CompiledStep> branches = step.inputs().stream().map(this::compileStep).toList();
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("Union requires at least one input");
        }
        List<String> visible = HiddenColumns.visible(branches.get(0).table());
        List<CompiledTablePlan.HiddenErrorColumn> hidden = HiddenColumns.union(branches);
        Table result = HiddenColumns.alignUnionBranch(branches.get(0), visible, hidden);
        for (int index = 1; index < branches.size(); index++) {
            result = result.unionAll(
                    HiddenColumns.alignUnionBranch(branches.get(index), visible, hidden)
            );
        }
        return new CompiledStep(result, hidden);
    }

    private CompiledStep aggregate(CompiledStep input, LogicalPlanNode.Aggregate aggregate) {
        List<Expression> selected = new ArrayList<>();
        for (String field : aggregate.groupBy()) {
            selected.add(Expressions.$(field));
        }
        for (LogicalPlanNode.Aggregation aggregation : aggregate.aggregations()) {
            selected.add(aggregationExpression(aggregation));
        }
        for (CompiledTablePlan.HiddenErrorColumn column : input.hiddenColumns()) {
            selected.add(HiddenColumns.flagAggregate(column.flagColumn()));
            selected.add(HiddenColumns.originalAggregate(column.originalColumn()));
        }
        Table aggregated;
        if (aggregate.groupBy().isEmpty()) {
            aggregated = input.table().select(selected.toArray(Expression[]::new));
        } else {
            GroupedTable grouped = input.table().groupBy(aggregate.groupBy().stream()
                    .map(Expressions::$)
                    .toArray(Expression[]::new));
            aggregated = grouped.select(selected.toArray(Expression[]::new));
        }
        List<CompiledTablePlan.HiddenErrorColumn> hidden = input.hiddenColumns().stream()
                .map(column -> column.withRowPrecise(false))
                .toList();
        return new CompiledStep(aggregated, hidden);
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

    private CompiledTablePlan.HiddenErrorColumn hiddenColumn(PlanStep step, String field) {
        int sequence = hiddenSequence++;
        return new CompiledTablePlan.HiddenErrorColumn(
                step.nodeId(),
                "in",
                field,
                HiddenColumns.flag(sequence),
                HiddenColumns.original(sequence),
                true
        );
    }
}
