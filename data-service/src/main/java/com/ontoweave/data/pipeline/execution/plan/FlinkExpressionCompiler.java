package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.table.api.ApiExpression;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.types.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FlinkExpressionCompiler {

    ApiExpression condition(JsonNode condition) {
        if (condition == null || !condition.isObject()) {
            throw new IllegalArgumentException("Filter condition is required");
        }
        String kind = condition.path("kind").asText("");
        return switch (kind) {
            case "and", "or" -> logicalConditions(condition, kind);
            case "compare" -> compareCondition(condition);
            case "is_null" -> Expressions.$(condition.path("field").asText()).isNull();
            case "is_not_null" -> Expressions.$(condition.path("field").asText()).isNotNull();
            default -> throw new IllegalArgumentException("Unsupported filter condition: " + kind);
        };
    }

    ApiExpression logicalConditions(JsonNode condition, String kind) {
        JsonNode children = condition.path("conditions");
        if (!children.isArray() || children.size() < 2) {
            throw new IllegalArgumentException("Logical condition requires at least two children");
        }
        ApiExpression result = condition(children.get(0));
        for (int index = 1; index < children.size(); index++) {
            result = "and".equals(kind) ? result.and(condition(children.get(index)))
                    : result.or(condition(children.get(index)));
        }
        return result;
    }

    ApiExpression compareCondition(JsonNode condition) {
        ApiExpression field = Expressions.$(condition.path("field").asText());
        String operator = condition.path("op").asText("");
        if ("in".equals(operator)) {
            JsonNode values = condition.path("values");
            List<ApiExpression> literals = new ArrayList<>();
            values.forEach(value -> literals.add(literal(value)));
            return field.in(literals.toArray(ApiExpression[]::new));
        }
        ApiExpression value = literal(condition.path("value"));
        return switch (operator) {
            case "eq" -> field.isEqual(value);
            case "ne" -> field.isNotEqual(value);
            case "gt" -> field.isGreater(value);
            case "gte" -> field.isGreaterOrEqual(value);
            case "lt" -> field.isLess(value);
            case "lte" -> field.isLessOrEqual(value);
            default -> throw new IllegalArgumentException("Unsupported comparison: " + operator);
        };
    }

    ApiExpression expression(JsonNode expression) {
        if (expression == null || !expression.isObject()) {
            throw new IllegalArgumentException("Expression is required");
        }
        String kind = expression.path("kind").asText("");
        return switch (kind) {
            case "field" -> Expressions.$(expression.path("field").asText());
            case "literal" -> literal(expression);
            case "cast" -> expression(expression.path("expression"))
                    .tryCast(dataType(expression.path("dataType").asText(null)));
            case "case" -> caseExpression(expression);
            case "compare" -> compareExpression(expression);
            case "logical" -> logicalExpression(expression);
            case "isNull" -> expression(expression.path("expression")).isNull();
            default -> throw new IllegalArgumentException("Unsupported expression: " + kind);
        };
    }

    ApiExpression literal(JsonNode expression) {
        String dataType = expression.path("dataType").asText(null);
        JsonNode value = expression.has("value") ? expression.path("value") : expression.path("value").path("value");
        return literal(value, dataType);
    }

    ApiExpression literal(JsonNode value, String dataType) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Expressions.nullOf(dataType(dataType));
        }
        String normalized = normalize(dataType);
        if (value.isBoolean()) {
            return Expressions.lit(value.asBoolean(), DataTypes.BOOLEAN());
        }
        if (value.isIntegralNumber()) {
            return Expressions.lit(value.asLong(), integralType(normalized));
        }
        if (value.isFloatingPointNumber()) {
            return Expressions.lit(value.asDouble(), DataTypes.DOUBLE());
        }
        return Expressions.lit(value.asText(), DataTypes.STRING());
    }

    ApiExpression caseExpression(JsonNode expression) {
        JsonNode otherwise = expression.path("otherwise");
        ApiExpression result = expression(otherwise);
        JsonNode branches = expression.path("branches");
        for (int index = branches.size() - 1; index >= 0; index--) {
            JsonNode branch = branches.get(index);
            result = Expressions.ifThenElse(
                    expression(branch.path("when")),
                    expression(branch.path("then")),
                    result
            );
        }
        return result;
    }

    ApiExpression compareExpression(JsonNode expression) {
        ApiExpression left = expression(expression.path("left"));
        ApiExpression right = expression(expression.path("right"));
        return switch (expression.path("op").asText("")) {
            case "eq" -> left.isEqual(right);
            case "ne" -> left.isNotEqual(right);
            case "gt" -> left.isGreater(right);
            case "gte" -> left.isGreaterOrEqual(right);
            case "lt" -> left.isLess(right);
            case "lte" -> left.isLessOrEqual(right);
            default -> throw new IllegalArgumentException("Unsupported compare expression");
        };
    }

    ApiExpression logicalExpression(JsonNode expression) {
        JsonNode children = expression.path("expressions");
        ApiExpression result = expression(children.get(0));
        boolean and = "and".equals(expression.path("op").asText());
        for (int index = 1; index < children.size(); index++) {
            result = and ? result.and(expression(children.get(index)))
                    : result.or(expression(children.get(index)));
        }
        return result;
    }

    DataType dataType(String value) {
        String normalized = normalize(value);
        if (normalized.startsWith("decimal")) {
            return DataTypes.DECIMAL(38, 18);
        }
        return switch (normalized) {
            case "boolean", "bool" -> DataTypes.BOOLEAN();
            case "tinyint", "byte" -> DataTypes.TINYINT();
            case "smallint", "short" -> DataTypes.SMALLINT();
            case "int", "integer" -> DataTypes.INT();
            case "bigint", "long" -> DataTypes.BIGINT();
            case "float", "real" -> DataTypes.FLOAT();
            case "double" -> DataTypes.DOUBLE();
            case "date" -> DataTypes.DATE();
            case "timestamp", "datetime" -> DataTypes.TIMESTAMP(3);
            case "binary", "bytes", "varbinary" -> DataTypes.BYTES();
            default -> DataTypes.STRING();
        };
    }

    DataType integralType(String type) {
        return switch (type) {
            case "int", "integer" -> DataTypes.INT();
            case "smallint", "short" -> DataTypes.SMALLINT();
            case "tinyint", "byte" -> DataTypes.TINYINT();
            default -> DataTypes.BIGINT();
        };
    }

    String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
