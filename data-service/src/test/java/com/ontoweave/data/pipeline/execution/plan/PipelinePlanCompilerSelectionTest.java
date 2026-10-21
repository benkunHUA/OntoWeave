package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.execution.flink.PipelineExecutionException;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.expressions.Expression;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PipelinePlanCompilerSelectionTest extends PipelinePlanCompilerTestSupport {

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

    @Test
    void deriveReplacesAnExistingColumnWithTheSameName() {
        PlanStep scan = scan("orders");
        PlanStep derive = derive(scan, "value", cast("id", "string"));

        CompiledTablePlan compiled = compiler.compile(
                plan(derive), mock(StreamTableEnvironment.class),
                ignored -> table("id", "value")
        );

        assertThat(compiled.table().getResolvedSchema().getColumnNames().stream()
                .filter(column -> !column.startsWith(HIDDEN_PREFIX)))
                .containsExactly("id", "value");
        assertThat(lastAdded).extracting(Expression::asSummaryString)
                .anySatisfy(summary -> assertThat(summary).contains("'value'"));
    }

    @Test
    void selectIncludeFollowsUserColumnOrderAndAppendsHiddenColumns() {
        PlanStep scan = scan("orders");
        PlanStep derive = derive(scan, "parsed", cast("value", "long"));
        PlanStep select = step("select", new LogicalPlanNode.SelectColumns(
                "include", List.of("parsed", "id")
        ), derive);

        CompiledTablePlan compiled = compiler.compile(
                plan(select), mock(StreamTableEnvironment.class),
                ignored -> table("id", "value")
        );

        String flag = compiled.hiddenErrorColumns().get(0).flagColumn();
        String original = compiled.hiddenErrorColumns().get(0).originalColumn();
        assertThat(lastSelected).extracting(this::outputName)
                .containsExactly("parsed", "id", flag, original);
    }
}
