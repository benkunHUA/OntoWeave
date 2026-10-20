package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineGraphExpressionTest extends PipelineGraphValidatorTestSupport {

    @Test
    void rejectsNonBooleanCaseCondition() {
        ObjectNode expression = caseExpression(
                literal("string", mapper.getNodeFactory().textNode("yes")),
                literal("string", mapper.getNodeFactory().textNode("a")),
                literal("string", mapper.getNodeFactory().textNode("b"))
        );

        assertTypeMismatch(expression, "expression.branches.when");
    }

    @Test
    void rejectsIncompatibleCaseResultTypes() {
        ObjectNode expression = caseExpression(
                literal("boolean", mapper.getNodeFactory().booleanNode(true)),
                literal("string", mapper.getNodeFactory().textNode("a")),
                literal("long", mapper.getNodeFactory().numberNode(1))
        );

        assertTypeMismatch(expression, "expression.otherwise");
    }

    @Test
    void rejectsLiteralValueThatDoesNotMatchItsDeclaredType() {
        ObjectNode expression = literal("boolean", mapper.getNodeFactory().textNode("yes"));

        assertTypeMismatch(expression, "expression.value");
    }

    private void assertTypeMismatch(ObjectNode expression, String field) {
        List<PipelineIssue> issues = validate(deriveDraft(expression), Map.of(
                expressionDatasetId, schema("id", "long")
        ));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("TYPE_MISMATCH");
            assertThat(issue.phase()).isEqualTo("schema");
            assertThat(issue.blocking()).isTrue();
            assertThat(issue.field()).isEqualTo(field);
        });
    }

    private final UUID expressionDatasetId = UUID.randomUUID();

    private ObjectNode deriveDraft(ObjectNode expression) {
        ObjectNode config = mapper.createObjectNode();
        config.put("outputField", "result");
        config.set("expression", expression);
        return draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(expressionDatasetId)))
                        .add(node(FILTER, "derive_column", config)),
                mapper.createArrayNode().add(edge(INPUT_A, "out", FILTER, "in"))
        );
    }

    private ObjectNode caseExpression(ObjectNode when, ObjectNode then, ObjectNode otherwise) {
        ObjectNode expression = mapper.createObjectNode();
        expression.put("kind", "case");
        expression.putArray("branches").addObject()
                .set("when", when);
        ((ObjectNode) expression.path("branches").get(0)).set("then", then);
        expression.set("otherwise", otherwise);
        return expression;
    }

    private ObjectNode literal(String dataType, com.fasterxml.jackson.databind.JsonNode value) {
        ObjectNode literal = mapper.createObjectNode();
        literal.put("kind", "literal");
        literal.put("dataType", dataType);
        literal.set("value", value);
        return literal;
    }
}
