package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.table.api.ApiExpression;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Expressions;

import java.util.ArrayList;
import java.util.List;

final class FlinkExpressionCompiler {

    private final FlinkLiteralCompiler literals = new FlinkLiteralCompiler();

    CompiledExpression compile(JsonNode expression) {
        if (expression == null || !expression.isObject()) {
            throw new IllegalArgumentException("Expression is required");
        }
        String kind = expression.path("kind").asText("");
        return switch (kind) {
            case "field" -> new CompiledExpression(
                    Expressions.$(expression.path("field").asText()), List.of()
            );
            case "literal" -> new CompiledExpression(literals.literal(expression), List.of());
            case "cast" -> cast(expression);
            case "case" -> caseExpression(expression);
            case "compare" -> compareExpression(expression);
            case "logical" -> logicalExpression(expression);
            case "isNull" -> {
                CompiledExpression source = compile(expression.path("expression"));
                yield new CompiledExpression(
                        source.expression().isNull(), source.failures()
                );
            }
            default -> throw new IllegalArgumentException("Unsupported expression: " + kind);
        };
    }

    record CompiledExpression(ApiExpression expression, List<CastFailure> failures) {

        CompiledExpression {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }
    }

    record CastFailure(ApiExpression flag, ApiExpression original) {
    }

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
            values.forEach(value -> literals.add(this.literals.literal(value)));
            return field.in(literals.toArray(ApiExpression[]::new));
        }
        ApiExpression value = this.literals.literal(condition.path("value"));
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
        return compile(expression).expression();
    }

    private CompiledExpression cast(JsonNode expression) {
        CompiledExpression source = compile(expression.path("expression"));
        ApiExpression raw = source.expression();
        ApiExpression casted = raw.tryCast(literals.dataType(expression.path("dataType").asText(null)));
        List<CastFailure> failures = new ArrayList<>(source.failures());
        failures.add(new CastFailure(
                casted.isNull().and(raw.isNotNull()),
                raw.tryCast(DataTypes.STRING())
        ));
        return new CompiledExpression(casted, failures);
    }

    CompiledExpression caseExpression(JsonNode expression) {
        CompiledExpression otherwise = compile(expression.path("otherwise"));
        ApiExpression result = otherwise.expression();
        JsonNode branches = expression.path("branches");
        List<CompiledExpression> whens = new ArrayList<>();
        List<CompiledExpression> thens = new ArrayList<>();
        List<ApiExpression> matchedConditions = new ArrayList<>();
        for (int index = 0; index < branches.size(); index++) {
            whens.add(compile(branches.get(index).path("when")));
            thens.add(compile(branches.get(index).path("then")));
        }
        ApiExpression priorUnmatched = null;
        List<CastFailure> failures = new ArrayList<>();
        for (int index = 0; index < whens.size(); index++) {
            ApiExpression matched = whens.get(index).expression().isTrue();
            matchedConditions.add(matched);
            failures.addAll(guarded(whens.get(index).failures(), priorUnmatched));
            ApiExpression branchGuard = priorUnmatched == null ? matched : priorUnmatched.and(matched);
            failures.addAll(guarded(thens.get(index).failures(), branchGuard));
            ApiExpression unmatched = matched.not();
            priorUnmatched = priorUnmatched == null ? unmatched : priorUnmatched.and(unmatched);
        }
        failures.addAll(guarded(otherwise.failures(), priorUnmatched));
        for (int index = branches.size() - 1; index >= 0; index--) {
            result = Expressions.ifThenElse(
                    matchedConditions.get(index),
                    thens.get(index).expression(),
                    result
            );
        }
        return new CompiledExpression(result, failures);
    }

    private List<CastFailure> guarded(List<CastFailure> failures, ApiExpression guard) {
        if (guard == null || failures.isEmpty()) {
            return failures;
        }
        return failures.stream()
                .map(failure -> new CastFailure(failure.flag().and(guard), failure.original()))
                .toList();
    }

    CompiledExpression compareExpression(JsonNode expression) {
        CompiledExpression left = compile(expression.path("left"));
        CompiledExpression right = compile(expression.path("right"));
        ApiExpression value = switch (expression.path("op").asText("")) {
            case "eq" -> left.expression().isEqual(right.expression());
            case "ne" -> left.expression().isNotEqual(right.expression());
            case "gt" -> left.expression().isGreater(right.expression());
            case "gte" -> left.expression().isGreaterOrEqual(right.expression());
            case "lt" -> left.expression().isLess(right.expression());
            case "lte" -> left.expression().isLessOrEqual(right.expression());
            default -> throw new IllegalArgumentException("Unsupported compare expression");
        };
        List<CastFailure> failures = new ArrayList<>(left.failures());
        failures.addAll(right.failures());
        return new CompiledExpression(value, failures);
    }

    CompiledExpression logicalExpression(JsonNode expression) {
        JsonNode children = expression.path("expressions");
        CompiledExpression first = compile(children.get(0));
        ApiExpression result = first.expression();
        List<CastFailure> failures = new ArrayList<>(first.failures());
        boolean and = "and".equals(expression.path("op").asText());
        for (int index = 1; index < children.size(); index++) {
            CompiledExpression child = compile(children.get(index));
            failures.addAll(child.failures());
            result = and ? result.and(child.expression()) : result.or(child.expression());
        }
        return new CompiledExpression(result, failures);
    }

}
