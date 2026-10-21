package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.ApiExpression;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlinkCaseGuardTableEnvironmentTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void laterBranchesDoNotReportFailuresWhenAnEarlierBranchMatches() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setString("security.delegation.token.provider.hadoopfs.enabled", "false");
        StreamExecutionEnvironment execution = StreamExecutionEnvironment.getExecutionEnvironment(
                configuration
        );
        execution.setRuntimeMode(RuntimeExecutionMode.BATCH);
        StreamTableEnvironment environment = StreamTableEnvironment.create(execution);
        Table source = environment.fromValues(Row.of(true, true, "10", "bad", "bad"))
                .select(
                        Expressions.$("f0").as("a"),
                        Expressions.$("f1").as("b"),
                        Expressions.$("f2").as("v1"),
                        Expressions.$("f3").as("v2"),
                        Expressions.$("f4").as("v3")
                );
        ObjectNode conditional = mapper.createObjectNode().put("kind", "case");
        ArrayNode branches = conditional.putArray("branches");
        branch(branches, "a", cast("v1"));
        branch(branches, "b", cast("v2"));
        conditional.set("otherwise", cast("v3"));

        FlinkExpressionCompiler.CompiledExpression compiled =
                new FlinkExpressionCompiler().compile(conditional);
        List<ApiExpression> columns = new ArrayList<>();
        columns.add(compiled.expression().as("result"));
        for (int index = 0; index < compiled.failures().size(); index++) {
            columns.add(compiled.failures().get(index).flag().as("flag_" + index));
        }

        try (var rows = source.select(columns.toArray(ApiExpression[]::new)).execute().collect()) {
            Row row = rows.next();
            assertThat(row.getField(0)).isEqualTo(10L);
            assertThat(row.getField(1)).isEqualTo(false);
            assertThat(row.getField(2)).isEqualTo(false);
            assertThat(row.getField(3)).isEqualTo(false);
        }
    }

    @Test
    void onlyTheSelectedBranchReportsAThatBranchFailureWhenEveryWhenMatches() throws Exception {
        StreamTableEnvironment environment = environment();
        Table source = source(environment, Row.of(true, true, "bad", "bad", "bad"));
        ObjectNode conditional = conditional("a", "b");

        Row row = evaluate(environment, source, conditional);

        assertThat(row.getField(0)).isNull();
        assertThat(row.getField(1)).isEqualTo(true);
        assertThat(row.getField(2)).isEqualTo(false);
        assertThat(row.getField(3)).isEqualTo(false);
    }

    @Test
    void whenCastFailureDoesNotRequireAThenCastFailure() throws Exception {
        StreamTableEnvironment environment = environment();
        Table source = source(environment, Row.of("bad", "10", "good", "bad", "11"));
        ObjectNode conditional = mapper.createObjectNode().put("kind", "case");
        ArrayNode branches = conditional.putArray("branches");
        branch(branches, cast("a", "boolean"), cast("v2"));
        conditional.set("otherwise", cast("v3"));

        Row row = evaluate(environment, source, conditional);

        assertThat(row.getField(1)).isEqualTo(true);
        assertThat(row.getField(2)).isEqualTo(false);
        assertThat(row.getField(3)).isEqualTo(false);
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

    private Table source(StreamTableEnvironment environment, Row values) {
        return environment.fromValues(values).select(
                Expressions.$("f0").as("a"),
                Expressions.$("f1").as("b"),
                Expressions.$("f2").as("v1"),
                Expressions.$("f3").as("v2"),
                Expressions.$("f4").as("v3")
        );
    }

    private ObjectNode conditional(String firstWhen, String secondWhen) {
        ObjectNode conditional = mapper.createObjectNode().put("kind", "case");
        ArrayNode branches = conditional.putArray("branches");
        branch(branches, firstWhen, cast("v1"));
        branch(branches, secondWhen, cast("v2"));
        conditional.set("otherwise", cast("v3"));
        return conditional;
    }

    private Row evaluate(
            StreamTableEnvironment environment,
            Table source,
            ObjectNode conditional
    ) throws Exception {
        FlinkExpressionCompiler.CompiledExpression compiled =
                new FlinkExpressionCompiler().compile(conditional);
        List<ApiExpression> columns = new ArrayList<>();
        columns.add(compiled.expression().as("result"));
        for (int index = 0; index < compiled.failures().size(); index++) {
            columns.add(compiled.failures().get(index).flag().as("flag_" + index));
        }
        try (var rows = source.select(columns.toArray(ApiExpression[]::new)).execute().collect()) {
            return rows.next();
        }
    }

    private void branch(ArrayNode branches, String when, ObjectNode then) {
        ObjectNode branch = branches.addObject();
        branch.set("when", field(when));
        branch.set("then", then);
    }

    private void branch(ArrayNode branches, ObjectNode when, ObjectNode then) {
        ObjectNode branch = branches.addObject();
        branch.set("when", when);
        branch.set("then", then);
    }

    private ObjectNode cast(String field) {
        return cast(field, "bigint");
    }

    private ObjectNode cast(String field, String dataType) {
        return mapper.createObjectNode()
                .put("kind", "cast")
                .put("dataType", dataType)
                .set("expression", field(field));
    }

    private ObjectNode field(String name) {
        return mapper.createObjectNode().put("kind", "field").put("field", name);
    }
}
