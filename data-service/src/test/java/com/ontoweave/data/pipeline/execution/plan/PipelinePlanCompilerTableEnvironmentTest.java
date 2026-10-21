package com.ontoweave.data.pipeline.execution.plan;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelinePlanCompilerTableEnvironmentTest extends PipelinePlanCompilerTestSupport {

    @Test
    void sameNameDeriveOverwritesTheVisibleColumnInARealTableEnvironment() {
        StreamTableEnvironment environment = environment();
        Table source = environment.fromValues(Row.of(7L, "old"))
                .select(Expressions.$("f0").as("id"), Expressions.$("f1").as("value"));
        PlanStep scan = scan("orders");
        PlanStep derive = derive(scan, "value", cast("id", "string"));

        CompiledTablePlan compiled = compiler.compile(plan(derive), environment, ignored -> source);

        assertThat(compiled.table().getResolvedSchema().getColumnNames())
                .filteredOn(name -> !name.startsWith(HIDDEN_PREFIX))
                .containsExactly("id", "value");
        assertThat(compiled.hiddenErrorColumns()).singleElement().satisfies(hidden -> {
            assertThat(compiled.table().getResolvedSchema().getColumnNames())
                    .contains(hidden.flagColumn(), hidden.originalColumn());
        });
    }

    @Test
    void selectIncludeUsesUserOrderInARealTableEnvironment() {
        StreamTableEnvironment environment = environment();
        Table source = environment.fromValues(Row.of("7", "old"))
                .select(Expressions.$("f0").as("id"), Expressions.$("f1").as("value"));
        PlanStep scan = scan("orders");
        PlanStep derive = derive(scan, "parsed", cast("id", "bigint"));
        PlanStep select = step("select", new LogicalPlanNode.SelectColumns(
                "include", List.of("parsed", "id")
        ), derive);

        CompiledTablePlan compiled = compiler.compile(plan(select), environment, ignored -> source);

        assertThat(compiled.table().getResolvedSchema().getColumnNames())
                .filteredOn(name -> !name.startsWith(HIDDEN_PREFIX))
                .containsExactly("parsed", "id");
    }

    private StreamTableEnvironment environment() {
        Configuration configuration = new Configuration();
        configuration.setString("security.delegation.token.provider.hadoopfs.enabled", "false");
        StreamExecutionEnvironment execution = StreamExecutionEnvironment.getExecutionEnvironment(
                configuration
        );
        execution.setRuntimeMode(RuntimeExecutionMode.BATCH);
        return StreamTableEnvironment.create(execution);
    }
}
