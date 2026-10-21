package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.pipeline.execution.flink.PipelineExecutionException;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.GroupedTable;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.expressions.Expression;
import org.apache.flink.table.types.DataType;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinePlanCompilerTest {

    private static final String HIDDEN_PREFIX = "__ontoweave_preview_";

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelinePlanCompiler compiler = compiler();
    private List<Expression> lastSelected = List.of();
    private List<Expression> lastGroupBy = List.of();

    @Test
    void keepsJoinRightBranchCastFlagsHiddenWithSynchronizedDescriptors() {
        PlanStep leftScan = scan("left");
        PlanStep leftDerive = derive(leftScan, "left_parsed", cast("value", "long"));
        PlanStep rightScan = scan("right");
        PlanStep rightDerive = derive(rightScan, "right_parsed", cast("value", "long"));
        PlanStep join = step("join", new LogicalPlanNode.Join(
                "inner", List.of(new LogicalPlanNode.JoinKey("id", "rid"))
        ), leftDerive, rightDerive);
        Function<LogicalPlanNode.ScanInput, Table> sources = scan ->
                table("left".equals(scan.table())
                        ? new String[]{"id", "value"}
                        : new String[]{"rid", "value"});

        CompiledTablePlan compiled = compiler.compile(plan(join), mock(StreamTableEnvironment.class), sources);

        List<String> schema = compiled.table().getResolvedSchema().getColumnNames();
        assertThat(compiled.hiddenErrorColumns()).hasSize(2);
        assertThat(compiled.hiddenErrorColumns())
                .allSatisfy(column -> {
                    assertThat(column.flagColumn()).startsWith(HIDDEN_PREFIX);
                    assertThat(column.originalColumn()).startsWith(HIDDEN_PREFIX);
                    assertThat(schema).contains(column.flagColumn(), column.originalColumn());
                });
        assertThat(compiled.hiddenErrorColumns())
                .extracting(CompiledTablePlan.HiddenErrorColumn::nodeId)
                .containsExactly(leftDerive.nodeId(), rightDerive.nodeId());
        assertThat(schema)
                .noneMatch(column -> column.contains("ontoweave") && !column.startsWith(HIDDEN_PREFIX));
    }

    @Test
    void aggregateKeepsUpstreamCastFlagsAndMarksRowsImprecise() {
        PlanStep scan = scan("orders");
        PlanStep derive = derive(scan, "parsed", cast("value", "long"));
        PlanStep aggregate = step("aggregate", new LogicalPlanNode.Aggregate(
                List.of("id"),
                List.of(new LogicalPlanNode.Aggregation("count", "value", "count"))
        ), derive);

        CompiledTablePlan compiled = compiler.compile(
                plan(aggregate), mock(StreamTableEnvironment.class),
                ignored -> table("id", "value")
        );

        assertThat(compiled.hiddenErrorColumns()).singleElement().satisfies(column -> {
            assertThat(column.nodeId()).isEqualTo(derive.nodeId());
            assertThat(column.field()).isEqualTo("parsed");
            assertThat(column.rowPrecise()).isFalse();
            assertThat(compiled.table().getResolvedSchema().getColumnNames())
                    .contains(column.flagColumn(), column.originalColumn());
        });
    }

    @Test
    void unionAlignsAndRetainsFlagsFromBothBranches() {
        PlanStep leftScan = scan("left");
        PlanStep leftDerive = derive(leftScan, "parsed", cast("value", "long"));
        PlanStep rightScan = scan("right");
        PlanStep rightDerive = derive(rightScan, "parsed", cast("value", "long"));
        PlanStep union = step("union", new LogicalPlanNode.Union(), leftDerive, rightDerive);

        CompiledTablePlan compiled = compiler.compile(
                plan(union), mock(StreamTableEnvironment.class),
                ignored -> table("id", "value")
        );

        assertThat(compiled.hiddenErrorColumns()).hasSize(2);
        assertThat(compiled.hiddenErrorColumns())
                .extracting(CompiledTablePlan.HiddenErrorColumn::nodeId)
                .containsExactly(leftDerive.nodeId(), rightDerive.nodeId());
        List<String> schema = compiled.table().getResolvedSchema().getColumnNames();
        assertThat(compiled.hiddenErrorColumns()).allSatisfy(column ->
                assertThat(schema).contains(column.flagColumn(), column.originalColumn()));
    }

    @Test
    void fullDeduplicateMergesVisibleDuplicatesAndKeepsAnyFailureFlag() {
        PlanStep scan = scan("orders");
        PlanStep derive = derive(scan, "parsed", cast("value", "long"));
        PlanStep deduplicate = step("deduplicate", new LogicalPlanNode.Deduplicate(
                "all", List.of()
        ), derive);

        CompiledTablePlan compiled = compiler.compile(
                plan(deduplicate), mock(StreamTableEnvironment.class),
                ignored -> table("id", "value")
        );

        String flag = compiled.hiddenErrorColumns().get(0).flagColumn();
        String original = compiled.hiddenErrorColumns().get(0).originalColumn();
        assertThat(groupByNames()).containsExactly("id", "value", "parsed");
        assertThat(groupByNames()).doesNotContain(flag, original);
        assertThat(lastSelected)
                .extracting(Expression::asSummaryString)
                .anySatisfy(summary -> assertThat(summary)
                        .containsIgnoringCase("max")
                        .contains(flag));
        assertThat(lastSelected)
                .extracting(Expression::asSummaryString)
                .anySatisfy(summary -> assertThat(summary)
                        .containsIgnoringCase("first_value")
                        .contains(original));
        assertThat(compiled.hiddenErrorColumns().get(0).rowPrecise()).isFalse();
    }

    @Test
    void subsetDeduplicateGroupsVisibleKeysAndKeepsAnyFailureFlag() {
        PlanStep scan = scan("orders");
        PlanStep derive = derive(scan, "parsed", cast("value", "long"));
        PlanStep deduplicate = step("deduplicate", new LogicalPlanNode.Deduplicate(
                "subset", List.of("id")
        ), derive);

        CompiledTablePlan compiled = compiler.compile(
                plan(deduplicate), mock(StreamTableEnvironment.class),
                ignored -> table("id", "value")
        );

        String flag = compiled.hiddenErrorColumns().get(0).flagColumn();
        String original = compiled.hiddenErrorColumns().get(0).originalColumn();
        assertThat(groupByNames()).containsExactly("id");
        assertThat(groupByNames()).doesNotContain(flag, original);
        assertThat(lastSelected)
                .extracting(Expression::asSummaryString)
                .anySatisfy(summary -> assertThat(summary)
                        .containsIgnoringCase("first_value")
                        .contains("value"));
        assertThat(lastSelected)
                .extracting(Expression::asSummaryString)
                .anySatisfy(summary -> assertThat(summary)
                        .containsIgnoringCase("max")
                        .contains(flag));
        assertThat(lastSelected)
                .extracting(Expression::asSummaryString)
                .anySatisfy(summary -> assertThat(summary).containsIgnoringCase("first_value"));
        assertThat(lastSelected)
                .extracting(Expression::asSummaryString)
                .anySatisfy(summary -> assertThat(summary)
                        .containsIgnoringCase("first_value")
                        .contains(original));
        assertThat(compiled.hiddenErrorColumns().get(0).rowPrecise()).isFalse();
    }

    @Test
    void joinCollisionRemainsHidden() {
        String name = HIDDEN_PREFIX + "0_flag";

        String renamed = HiddenColumns.uniqueJoinName(name, java.util.Set.of(name));

        assertThat(renamed).startsWith(HIDDEN_PREFIX).isNotEqualTo(name);
    }

    @Test
    void mapsExpressionCompileFailureToTheConcreteStep() {
        PlanStep scan = scan("orders");
        ObjectNode unsupported = mapper.createObjectNode().put("kind", "unsupported");
        PlanStep derive = derive(scan, "parsed", unsupported);

        assertThatThrownBy(() -> compiler.compile(
                plan(derive), mock(StreamTableEnvironment.class),
                ignored -> table("id", "value")
        )).isInstanceOfSatisfying(PipelineExecutionException.class, exception -> {
            assertThat(exception.nodeId()).isEqualTo(derive.nodeId());
            assertThat(exception.portId()).isEqualTo("in");
            assertThat(exception.field()).isNull();
        });
    }

    private PipelinePlanCompiler compiler() {
        SyncProperties properties = mock(SyncProperties.class);
        when(properties.icebergWarehouse()).thenReturn("/tmp/ontoweave-warehouse");
        return new PipelinePlanCompiler(properties);
    }

    private Table table(String... columns) {
        Table table = mock(Table.class);
        ResolvedSchema schema = ResolvedSchema.physical(columns, types(columns.length));
        when(table.getResolvedSchema()).thenReturn(schema);
        when(table.filter(any(Expression.class))).thenReturn(table);
        when(table.as(anyString())).thenReturn(table);
        when(table.distinct()).thenReturn(table);
        when(table.addColumns(any(Expression[].class))).thenAnswer(addColumns(columns));
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

    private List<String> groupByNames() {
        return lastGroupBy.stream().map(this::outputName).toList();
    }

    private DataType[] types(int count) {
        DataType[] types = new DataType[count];
        Arrays.fill(types, DataTypes.STRING());
        return types;
    }

    private Answer<Table> addColumns(String[] columns) {
        return invocation -> {
            List<String> names = new ArrayList<>(List.of(columns));
            names.addAll(expressions(invocation).stream()
                    .map(this::outputName)
                    .toList());
            return table(names.toArray(String[]::new));
        };
    }

    private Answer<Table> selectColumns() {
        return invocation -> {
            lastSelected = expressions(invocation);
            return table(lastSelected.stream()
                    .map(this::outputName)
                    .toArray(String[]::new));
        };
    }

    private Answer<Table> renameColumns(String[] columns) {
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

    private List<Expression> expressions(InvocationOnMock invocation) {
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

    private String outputName(Expression expression) {
        String summary = expression.asSummaryString();
        if (summary.startsWith("as(") && summary.endsWith("')")) {
            return summary.substring(summary.lastIndexOf(", '") + 3, summary.length() - 2);
        }
        return summary;
    }

    private String[] rename(String summary) {
        if (!summary.startsWith("as(") || !summary.endsWith("')")) {
            return new String[]{summary, summary};
        }
        String source = summary.substring(3, summary.lastIndexOf(", '"));
        String target = summary.substring(summary.lastIndexOf(", '") + 3, summary.length() - 2);
        return new String[]{source, target};
    }

    private PlanStep derive(PlanStep input, String outputField, ObjectNode expression) {
        return step(UUID.randomUUID().toString(), new LogicalPlanNode.DeriveColumn(
                outputField, expression
        ), input);
    }

    private PlanStep scan(String table) {
        return new PlanStep(
                UUID.randomUUID().toString(),
                "input",
                new LogicalPlanNode.ScanInput("ontoweave", "sales", table, 100L, 50_000),
                List.of()
        );
    }

    private PlanStep step(String nodeId, LogicalPlanNode node, PlanStep... inputs) {
        return new PlanStep(nodeId, nodeType(node), node, List.of(inputs));
    }

    private String nodeType(LogicalPlanNode node) {
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
        return "union";
    }

    private LogicalPlan plan(PlanStep root) {
        return LogicalPlan.of(root, 4L, root.nodeId(), Map.of());
    }

    private ObjectNode field(String name) {
        return mapper.createObjectNode().put("kind", "field").put("field", name);
    }

    private ObjectNode cast(String name, String dataType) {
        return mapper.createObjectNode()
                .put("kind", "cast")
                .put("dataType", dataType)
                .set("expression", field(name));
    }
}
