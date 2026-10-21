package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
