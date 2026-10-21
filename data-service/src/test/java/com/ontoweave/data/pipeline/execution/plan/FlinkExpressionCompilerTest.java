package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlinkExpressionCompilerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FlinkExpressionCompiler compiler = new FlinkExpressionCompiler();

    @Test
    void nestedCastProducesFailureMarkerForEachCast() {
        ObjectNode inner = cast(field("amount"), "long");
        ObjectNode outer = cast(inner, "string");

        FlinkExpressionCompiler.CompiledExpression compiled = compiler.compile(outer);

        assertThat(compiled.failures()).hasSize(2);
    }


    @Test
    void rejectsValuesThatDoNotMatchTheirDeclaredLiteralTypes() {
        assertThatThrownBy(() -> compiler.compile(literal("2026-13-40", "date")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
        assertThatThrownBy(() -> compiler.compile(literal("not-a-timestamp", "timestamp")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
        assertThatThrownBy(() -> compiler.compile(literal(200, "tinyint")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tinyint");
    }

    @Test
    void guardsCaseBranchCastFailuresWithBranchSelection() {
        ObjectNode conditional = mapper.createObjectNode().put("kind", "case");
        ArrayNode branches = conditional.putArray("branches");
        branch(branches, "is_a", cast(field("a"), "long"));
        branch(branches, "is_b", cast(field("b"), "long"));
        conditional.set("otherwise", cast(field("c"), "long"));

        FlinkExpressionCompiler.CompiledExpression compiled = compiler.compile(conditional);

        assertThat(compiled.failures()).hasSize(3);
        assertThat(compiled.failures().get(0).flag().asSummaryString())
                .contains("isTrue(is_a)");
        assertThat(compiled.failures().get(1).flag().asSummaryString())
                .contains("isTrue(is_b)");
        assertThat(compiled.failures().get(2).flag().asSummaryString())
                .contains("not(isTrue(is_a))")
                .contains("not(isTrue(is_b))");
    }

    private void branch(ArrayNode branches, String field, ObjectNode then) {
        ObjectNode branch = branches.addObject();
        branch.set("when", field(field));
        branch.set("then", then);
    }

    private ObjectNode literal(Object value, String dataType) {
        ObjectNode literal = mapper.createObjectNode().put("kind", "literal").put("dataType", dataType);
        literal.set("value", mapper.valueToTree(value));
        return literal;
    }

    private ObjectNode cast(ObjectNode expression, String dataType) {
        ObjectNode cast = mapper.createObjectNode();
        cast.put("kind", "cast");
        cast.put("dataType", dataType);
        cast.set("expression", expression);
        return cast;
    }

    private ObjectNode field(String name) {
        ObjectNode field = mapper.createObjectNode();
        field.put("kind", "field");
        field.put("field", name);
        return field;
    }
}
