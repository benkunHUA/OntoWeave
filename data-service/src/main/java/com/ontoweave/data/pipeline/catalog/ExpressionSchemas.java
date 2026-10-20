package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

import static com.ontoweave.data.pipeline.catalog.SchemaNodes.JSON;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.array;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.enumString;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.object;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.properties;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.reference;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.rootSchema;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.scalar;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.string;

final class ExpressionSchemas {

    private static final List<String> COMPARISON_OPS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte"
    );

    private ExpressionSchemas() {
    }

    static ObjectNode schema() {
        ObjectNode schema = rootSchema();
        ObjectNode expression = expression();
        ObjectNode properties = properties(schema);
        properties.set("outputField", string());
        properties.set("expression", expression);
        schema.putObject("$defs").set("expression", expression.deepCopy());
        return schema;
    }

    static ObjectNode expression() {
        ObjectNode expression = JSON.createObjectNode();
        ArrayNode variants = expression.putArray("oneOf");
        variants.add(fieldExpression());
        variants.add(literalExpression());
        variants.add(castExpression());
        variants.add(caseExpression());
        variants.add(compareExpression());
        variants.add(logicalExpression());
        variants.add(isNullExpression());
        return expression;
    }

    private static ObjectNode fieldExpression() {
        ObjectNode expression = expressionObject("field");
        required(expression, "field");
        properties(expression).set("field", string());
        return expression;
    }

    private static ObjectNode literalExpression() {
        ObjectNode expression = expressionObject("literal");
        required(expression, "value", "dataType");
        ObjectNode properties = properties(expression);
        properties.set("value", scalar());
        properties.set("dataType", string());
        return expression;
    }

    private static ObjectNode castExpression() {
        ObjectNode expression = expressionObject("cast");
        required(expression, "expression", "dataType");
        ObjectNode properties = properties(expression);
        properties.set("expression", reference("expression"));
        properties.set("dataType", string());
        return expression;
    }

    private static ObjectNode caseExpression() {
        ObjectNode expression = expressionObject("case");
        required(expression, "branches", "otherwise");
        ObjectNode properties = properties(expression);
        ObjectNode branches = array();
        branches.put("minItems", 1);
        ObjectNode item = object();
        required(item, "when", "then");
        properties(item).set("when", reference("expression"));
        properties(item).set("then", reference("expression"));
        branches.set("items", item);
        properties.set("branches", branches);
        properties.set("otherwise", reference("expression"));
        return expression;
    }

    private static ObjectNode compareExpression() {
        ObjectNode expression = expressionObject("compare");
        required(expression, "op", "left", "right");
        ObjectNode properties = properties(expression);
        properties.set("op", enumString(COMPARISON_OPS));
        properties.set("left", reference("expression"));
        properties.set("right", reference("expression"));
        return expression;
    }

    private static ObjectNode logicalExpression() {
        ObjectNode expression = expressionObject("logical");
        required(expression, "op", "expressions");
        ObjectNode properties = properties(expression);
        properties.set("op", enumString("and", "or"));
        ObjectNode expressions = array();
        expressions.put("minItems", 2);
        expressions.set("items", reference("expression"));
        properties.set("expressions", expressions);
        return expression;
    }

    private static ObjectNode isNullExpression() {
        ObjectNode expression = expressionObject("isNull");
        required(expression, "expression");
        properties(expression).set("expression", reference("expression"));
        return expression;
    }

    private static ObjectNode expressionObject(String kind) {
        ObjectNode expression = object();
        required(expression, "kind");
        properties(expression).set("kind", enumString(kind));
        return expression;
    }

    private static void required(ObjectNode schema, String... fields) {
        ArrayNode required = schema.putArray("required");
        for (String field : fields) {
            required.add(field);
        }
    }
}
