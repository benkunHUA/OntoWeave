package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.config.SyncProperties;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.GroupedTable;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.expressions.Expression;
import org.apache.flink.table.types.DataType;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class PipelinePlanCompilerTestSupport {

    static final String HIDDEN_PREFIX = "__ontoweave_preview_";

    final ObjectMapper mapper = new ObjectMapper();
    final PipelinePlanCompiler compiler = compiler();
    List<Expression> lastSelected = List.of();
    List<Expression> lastAdded = List.of();
    List<Expression> lastGroupBy = List.of();

    PipelinePlanCompiler compiler() {
        SyncProperties properties = mock(SyncProperties.class);
        when(properties.icebergWarehouse()).thenReturn("/tmp/ontoweave-warehouse");
        return new PipelinePlanCompiler(properties);
    }

    Table table(String... columns) {
        Table table = mock(Table.class);
        ResolvedSchema schema = ResolvedSchema.physical(columns, types(columns.length));
        when(table.getResolvedSchema()).thenReturn(schema);
        when(table.filter(any(Expression.class))).thenReturn(table);
        when(table.as(anyString())).thenReturn(table);
        when(table.distinct()).thenReturn(table);
        when(table.addOrReplaceColumns(any(org.apache.flink.table.api.ApiExpression[].class)))
                .thenAnswer(addOrReplaceColumns(columns));
        when(table.select(any(Expression[].class))).thenAnswer(selectColumns());
        when(table.renameColumns(any(Expression[].class))).thenAnswer(renameColumns(columns));
        when(table.join(any(), any(Expression.class))).thenReturn(table);
        when(table.leftOuterJoin(any(), any(Expression.class))).thenReturn(table);
        when(table.rightOuterJoin(any(), any(Expression.class))).thenReturn(table);
        when(table.fullOuterJoin(any(), any(Expression.class))).thenReturn(table);
        when(table.unionAll(any())).thenReturn(table);
        GroupedTable grouped = mock(GroupedTable.class);
        when(grouped.select(any(Expression[].class))).thenAnswer(selectColumns());
        when(table.groupBy(any(Expression[].class))).thenAnswer(invocation -> {
            lastGroupBy = expressions(invocation);
            return grouped;
        });
        return table;
    }

    List<String> groupByNames() {
        return lastGroupBy.stream().map(this::outputName).toList();
    }

    DataType[] types(int count) {
        DataType[] types = new DataType[count];
        Arrays.fill(types, DataTypes.STRING());
        return types;
    }

    Answer<Table> addOrReplaceColumns(String[] columns) {
        return invocation -> {
            lastAdded = expressions(invocation);
            List<String> names = new ArrayList<>(List.of(columns));
            for (Expression expression : lastAdded) {
                String name = outputName(expression);
                int index = names.indexOf(name);
                if (index >= 0) {
                    names.set(index, name);
                } else {
                    names.add(name);
                }
            }
            return table(names.toArray(String[]::new));
        };
    }

    Answer<Table> selectColumns() {
        return invocation -> {
            lastSelected = expressions(invocation);
            return table(lastSelected.stream()
                    .map(this::outputName)
                    .toArray(String[]::new));
        };
    }

    Answer<Table> renameColumns(String[] columns) {
        return invocation -> {
            List<String> names = new ArrayList<>(List.of(columns));
            for (Expression expression : expressions(invocation)) {
                String[] rename = rename(expression.asSummaryString());
                int index = names.indexOf(rename[0]);
                if (index >= 0) {
                    names.set(index, rename[1]);
                }
            }
            return table(names.toArray(String[]::new));
        };
    }

    List<Expression> expressions(InvocationOnMock invocation) {
        Object[] arguments = invocation.getArguments();
        if (arguments.length == 1 && arguments[0] instanceof Expression[] values) {
            return List.of(values);
        }
        List<Expression> values = new ArrayList<>();
        for (Object argument : arguments) {
            if (argument instanceof Expression expression) {
                values.add(expression);
            }
        }
        return values;
    }

    String outputName(Expression expression) {
        String summary = expression.asSummaryString();
        if (summary.startsWith("as(") && summary.endsWith("')")) {
            return summary.substring(summary.lastIndexOf(", '") + 3, summary.length() - 2);
        }
        return summary;
    }

    String[] rename(String summary) {
        if (!summary.startsWith("as(") || !summary.endsWith("')")) {
            return new String[]{summary, summary};
        }
        String source = summary.substring(3, summary.lastIndexOf(", '"));
        String target = summary.substring(summary.lastIndexOf(", '") + 3, summary.length() - 2);
        return new String[]{source, target};
    }

    PlanStep derive(PlanStep input, String outputField, ObjectNode expression) {
        return step(UUID.randomUUID().toString(), new LogicalPlanNode.DeriveColumn(
                outputField, expression
        ), input);
    }

    PlanStep scan(String table) {
        return new PlanStep(
                UUID.randomUUID().toString(),
                "input",
                new LogicalPlanNode.ScanInput(
                        "ontoweave", "sales", table, 100L, ScanLimit.preview(50_000)
                ),
                List.of()
        );
    }

    PlanStep step(String nodeId, LogicalPlanNode node, PlanStep... inputs) {
        return new PlanStep(nodeId, nodeType(node), node, List.of(inputs));
    }

    String nodeType(LogicalPlanNode node) {
        if (node instanceof LogicalPlanNode.DeriveColumn) {
            return "derive_column";
        }
        if (node instanceof LogicalPlanNode.Join) {
            return "join";
        }
        if (node instanceof LogicalPlanNode.Aggregate) {
            return "aggregate";
        }
        if (node instanceof LogicalPlanNode.Deduplicate) {
            return "deduplicate";
        }
        if (node instanceof LogicalPlanNode.SelectColumns) {
            return "select_columns";
        }
        return "union";
    }

    LogicalPlan plan(PlanStep root) {
        return LogicalPlan.of(root, 4L, root.nodeId(), Map.of());
    }

    ObjectNode field(String name) {
        return mapper.createObjectNode().put("kind", "field").put("field", name);
    }

    ObjectNode cast(String name, String dataType) {
        return mapper.createObjectNode()
                .put("kind", "cast")
                .put("dataType", dataType)
                .set("expression", field(name));
    }
}
