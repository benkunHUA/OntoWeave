package com.ontoweave.data.pipeline.execution.plan;

import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.expressions.Expression;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelinePlanCompilerTest extends PipelinePlanCompilerTestSupport {

    @Test
    void appliesIcebergLimitOnlyToLimitedScans() {
        StreamTableEnvironment environment = mock(StreamTableEnvironment.class);
        AtomicReference<String> query = new AtomicReference<>();
        when(environment.listCatalogs()).thenReturn(new String[]{"ontoweave"});
        when(environment.sqlQuery(anyString())).thenAnswer(invocation -> {
            query.set(invocation.getArgument(0));
            return table("id");
        });
        LogicalPlanNode.ScanInput limited = new LogicalPlanNode.ScanInput(
                "ontoweave", "sales", "orders", 100L, ScanLimit.preview(50_000)
        );
        LogicalPlanNode.ScanInput unlimited = new LogicalPlanNode.ScanInput(
                "ontoweave", "sales", "orders", 100L, ScanLimit.unlimited()
        );

        compiler.scan(limited, environment);
        assertThat(query.get())
                .contains("snapshot-id", "100", "LIMIT 50000");

        compiler.scan(unlimited, environment);
        assertThat(query.get()).doesNotContain("LIMIT");
    }

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
}
