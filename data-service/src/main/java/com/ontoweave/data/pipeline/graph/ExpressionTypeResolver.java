package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.ontoweave.data.pipeline.graph.SchemaValues.text;

final class ExpressionTypeResolver {

    private final List<FieldSchema> fields;
    private final String nodeId;
    private final List<PipelineIssue> issues;

    ExpressionTypeResolver(List<FieldSchema> fields, String nodeId, List<PipelineIssue> issues) {
        this.fields = fields;
        this.nodeId = nodeId;
        this.issues = issues;
    }

    void validateCondition(JsonNode condition) {
        if (condition == null || !condition.isObject()) {
            incomplete("condition");
            return;
        }
        String kind = condition.path("kind").asText(null);
        switch (kind == null ? "" : kind) {
            case "compare" -> validateCompare(condition);
            case "and", "or" -> {
                JsonNode conditions = condition.path("conditions");
                if (!conditions.isArray() || conditions.size() < 2) {
                    incomplete("conditions");
                    return;
                }
                conditions.forEach(this::validateCondition);
            }
            case "is_null", "is_not_null" -> requireField(condition.path("field").asText(null), "field");
            case "in" -> validateIn(condition);
            default -> incomplete("condition.kind");
        }
    }

    Optional<String> type(JsonNode expression) {
        if (expression == null || !expression.isObject()) {
            incomplete("expression");
            return Optional.empty();
        }
        String kind = expression.path("kind").asText(null);
        return switch (kind == null ? "" : kind) {
            case "field" -> requireField(expression.path("field").asText(null), "field");
            case "literal" -> literalType(expression);
            case "cast" -> {
                type(expression.path("expression"));
                String dataType = text(expression, "dataType");
                if (dataType == null) {
                    incomplete("expression.dataType");
                }
                yield Optional.ofNullable(dataType);
            }
            case "case" -> caseType(expression);
            case "compare" -> {
                compareTypes(expression.path("left"), expression.path("right"));
                yield Optional.of("boolean");
            }
            case "logical" -> {
                JsonNode expressions = expression.path("expressions");
                if (!expressions.isArray() || expressions.size() < 2) {
                    incomplete("expression.expressions");
                } else {
                    expressions.forEach(child -> {
                        Optional<String> type = type(child);
                        type.ifPresent(value -> requireBoolean(value, "expression.expressions"));
                    });
                }
                yield Optional.of("boolean");
            }
            case "isNull" -> {
                type(expression.path("expression"));
                yield Optional.of("boolean");
            }
            default -> {
                incomplete("expression.kind");
                yield Optional.empty();
            }
        };
    }

    private void validateCompare(JsonNode condition) {
        String field = condition.path("field").asText(null);
        Optional<String> fieldType = requireField(field, "field");
        String operator = condition.path("op").asText(null);
        if (operator == null) {
            incomplete("op");
            return;
        }
        if ("in".equals(operator)) {
            JsonNode values = condition.path("values");
            if (!values.isArray() || values.isEmpty()) {
                incomplete("values");
                return;
            }
            values.forEach(value -> compareValue(fieldType, value));
            return;
        }
        if ("is_null".equals(operator) || "is_not_null".equals(operator)) {
            return;
        }
        JsonNode value = condition.path("value");
        if (!value.isObject()) {
            incomplete("value");
            return;
        }
        compareValue(fieldType, value);
    }

    private void validateIn(JsonNode condition) {
        Optional<String> fieldType = requireField(condition.path("field").asText(null), "field");
        JsonNode values = condition.path("values");
        if (!values.isArray() || values.isEmpty()) {
            incomplete("values");
            return;
        }
        values.forEach(value -> compareValue(fieldType, value));
    }

    private void compareValue(Optional<String> fieldType, JsonNode literal) {
        String dataType = literal.path("dataType").asText(null);
        if (fieldType.isEmpty() || dataType == null) {
            return;
        }
        if (!compatible(fieldType.get(), dataType)) {
            issues.add(new PipelineIssue(
                    PipelineIssueCodes.TYPE_MISMATCH,
                    PipelineIssueCodes.PHASE_SCHEMA,
                    true,
                    nodeId,
                    null,
                    null,
                    "字段类型与比较值类型不兼容",
                    "将比较值转换为字段类型"
            ));
        }
    }

    private Optional<String> caseType(JsonNode expression) {
        JsonNode branches = expression.path("branches");
        if (!branches.isArray() || branches.isEmpty()) {
            incomplete("expression.branches");
        } else {
            for (JsonNode branch : branches) {
                type(branch.path("when"));
                type(branch.path("then"));
            }
        }
        Optional<String> result = type(expression.path("otherwise"));
        return result;
    }

    private Optional<String> literalType(JsonNode expression) {
        String dataType = text(expression, "dataType");
        if (dataType == null) {
            incomplete("expression.dataType");
        }
        return Optional.ofNullable(dataType);
    }

    private void requireBoolean(String type, String field) {
        if (!"boolean".equalsIgnoreCase(type)) {
            issues.add(new PipelineIssue(
                    PipelineIssueCodes.TYPE_MISMATCH,
                    PipelineIssueCodes.PHASE_SCHEMA,
                    true,
                    nodeId,
                    null,
                    field,
                    "逻辑表达式必须是 boolean 类型",
                    "使用布尔表达式"
            ));
        }
    }

    private void compareTypes(JsonNode left, JsonNode right) {
        Optional<String> leftType = type(left);
        Optional<String> rightType = type(right);
        if (leftType.isPresent() && rightType.isPresent()
                && !compatible(leftType.get(), rightType.get())) {
            issues.add(new PipelineIssue(
                    PipelineIssueCodes.TYPE_MISMATCH,
                    PipelineIssueCodes.PHASE_SCHEMA,
                    true,
                    nodeId,
                    null,
                    null,
                    "表达式两侧类型不兼容",
                    "统一表达式数据类型"
            ));
        }
    }

    private Optional<String> requireField(String name, String fieldPath) {
        if (name == null || name.isBlank()) {
            incomplete(fieldPath);
            return Optional.empty();
        }
        Optional<FieldSchema> field = fields.stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst();
        if (field.isEmpty()) {
            issues.add(new PipelineIssue(
                    PipelineIssueCodes.FIELD_NOT_FOUND,
                    PipelineIssueCodes.PHASE_SCHEMA,
                    true,
                    nodeId,
                    null,
                    name,
                    "字段不存在: " + name,
                    "选择上游已有字段"
            ));
            return Optional.empty();
        }
        return Optional.of(field.get().dataType());
    }

    private void incomplete(String field) {
        issues.add(new PipelineIssue(
                PipelineIssueCodes.NODE_CONFIG_INCOMPLETE,
                PipelineIssueCodes.PHASE_SCHEMA,
                true,
                nodeId,
                null,
                field,
                "节点配置不完整: " + field,
                "补全节点配置"
        ));
    }

    static boolean compatible(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return normalizedLeft.equals(normalizedRight)
                || (numeric(normalizedLeft) && numeric(normalizedRight));
    }

    static boolean numeric(String type) {
        String normalized = normalize(type);
        return normalized.contains("int")
                || normalized.contains("long")
                || normalized.contains("short")
                || normalized.contains("float")
                || normalized.contains("double")
                || normalized.contains("decimal")
                || normalized.equals("number");
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
