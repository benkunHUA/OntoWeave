package com.ontoweave.data.pipeline.execution.plan;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.expressions.Expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HiddenColumns {

    static final String PREFIX = CompiledTablePlan.HIDDEN_PREFIX;

    private HiddenColumns() {
    }

    static String flag(int sequence) {
        return PREFIX + sequence + "_flag";
    }

    static String original(int sequence) {
        return PREFIX + sequence + "_original";
    }

    static List<String> visible(Table table) {
        return table.getResolvedSchema().getColumnNames().stream()
                .filter(column -> !column.startsWith(PREFIX))
                .toList();
    }

    static Set<String> names(List<String> columns) {
        return columns.stream()
                .filter(column -> column.startsWith(PREFIX))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    static String uniqueJoinName(String name, Set<String> names) {
        if (!names.contains(name)) {
            return name;
        }
        if (name.startsWith(PREFIX)) {
            String suffix = name.substring(PREFIX.length());
            String candidate = PREFIX + "right_" + suffix;
            int index = 2;
            while (names.contains(candidate)) {
                candidate = PREFIX + "right_" + index++ + "_" + suffix;
            }
            return candidate;
        }
        String base = "right_" + name;
        String candidate = base;
        int index = 2;
        while (names.contains(candidate)) {
            candidate = base + "_" + index++;
        }
        return candidate;
    }

    static Expression flagAggregate(String flagColumn) {
        return Expressions.$(flagColumn).cast(DataTypes.INT()).max()
                .isEqual(Expressions.lit(1)).as(flagColumn);
    }

    static Expression originalAggregate(String originalColumn) {
        return Expressions.$(originalColumn).firstValue().as(originalColumn);
    }

    static List<CompiledTablePlan.HiddenErrorColumn> union(List<CompiledStep> branches) {
        Map<String, CompiledTablePlan.HiddenErrorColumn> hidden = new LinkedHashMap<>();
        for (CompiledStep branch : branches) {
            for (CompiledTablePlan.HiddenErrorColumn column : branch.hiddenColumns()) {
                hidden.putIfAbsent(column.flagColumn() + '\u0000' + column.originalColumn(), column);
            }
        }
        return new ArrayList<>(hidden.values());
    }

    static Table alignUnionBranch(
            CompiledStep branch,
            List<String> visible,
            List<CompiledTablePlan.HiddenErrorColumn> hidden
    ) {
        Set<String> schema = Set.copyOf(branch.table().getResolvedSchema().getColumnNames());
        List<Expression> selected = new ArrayList<>();
        for (String column : visible) {
            selected.add(Expressions.$(column));
        }
        for (CompiledTablePlan.HiddenErrorColumn column : hidden) {
            selected.add(schema.contains(column.flagColumn())
                    ? Expressions.$(column.flagColumn())
                    : Expressions.lit(false).as(column.flagColumn()));
            selected.add(schema.contains(column.originalColumn())
                    ? Expressions.$(column.originalColumn())
                    : Expressions.nullOf(DataTypes.STRING()).as(column.originalColumn()));
        }
        return branch.table().select(selected.toArray(Expression[]::new));
    }
}
