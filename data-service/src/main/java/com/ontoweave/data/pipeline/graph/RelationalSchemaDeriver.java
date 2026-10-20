package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.ontoweave.data.pipeline.graph.SchemaValues.text;

final class RelationalSchemaDeriver extends SchemaDeriverSupport {

    RelationalSchemaDeriver(String nodeId, List<PipelineIssue> issues) {
        super(nodeId, issues);
    }

    List<FieldSchema> join(JsonNode config, List<FieldSchema> left, List<FieldSchema> right) {
        String joinType = text(config, "joinType");
        if (joinType == null) {
            incomplete("joinType");
            return null;
        }
        JsonNode keys = config.path("keys");
        if (!keys.isArray()) {
            incomplete("keys");
            return null;
        }
        if (left == null || right == null) {
            return null;
        }
        for (JsonNode key : keys) {
            validateJoinKey(key.path("left").asText(null), left, "left");
            validateJoinKey(key.path("right").asText(null), right, "right");
            String leftType = typeOf(left, key.path("left").asText(null));
            String rightType = typeOf(right, key.path("right").asText(null));
            if (leftType != null && rightType != null
                    && !ExpressionTypeResolver.compatible(leftType, rightType)) {
                issues.add(issue(
                        PipelineIssueCodes.SCHEMA_MISMATCH,
                        null,
                        "关联键类型不一致",
                        "统一关联键类型"
                ));
            }
        }
        List<FieldSchema> output = new ArrayList<>(left);
        Set<String> names = new HashSet<>(left.stream().map(FieldSchema::name).toList());
        for (FieldSchema field : right) {
            String name = field.name();
            if (names.contains(name)) {
                name = "right_" + name;
            }
            if (!names.add(name)) {
                issues.add(issue(
                        PipelineIssueCodes.DUPLICATE_FIELD,
                        name,
                        "关联后字段重名",
                        "重命名冲突字段"
                ));
                continue;
            }
            output.add(new FieldSchema(name, field.dataType(), field.nullable()));
        }
        return output;
    }

    List<FieldSchema> union(List<List<FieldSchema>> inputs) {
        if (inputs == null || inputs.size() < 2) {
            return null;
        }
        List<FieldSchema> first = inputs.get(0);
        if (first == null) {
            return null;
        }
        for (int index = 1; index < inputs.size(); index++) {
            List<FieldSchema> other = inputs.get(index);
            if (other == null) {
                return null;
            }
            compareUnion(first, other);
        }
        return first;
    }

    private void compareUnion(List<FieldSchema> first, List<FieldSchema> other) {
        if (first.size() != other.size()) {
            issues.add(issue(
                    PipelineIssueCodes.SCHEMA_MISMATCH,
                    null,
                    "union 输入字段数量不一致",
                    "统一 union 输入 schema"
            ));
            return;
        }
        for (int index = 0; index < first.size(); index++) {
            FieldSchema left = first.get(index);
            FieldSchema right = other.get(index);
            if (!left.name().equals(right.name())
                    || !left.dataType().equalsIgnoreCase(right.dataType())) {
                issues.add(issue(
                        PipelineIssueCodes.SCHEMA_MISMATCH,
                        left.name(),
                        "union 输入字段名或类型不一致",
                        "统一 union 输入 schema"
                ));
                return;
            }
        }
    }

    private void validateJoinKey(String name, List<FieldSchema> schema, String side) {
        if (name == null || name.isBlank()) {
            incomplete(side);
        } else if (field(schema, name) == null) {
            fieldNotFound(name);
        }
    }

    private String typeOf(List<FieldSchema> fields, String name) {
        FieldSchema field = field(fields, name);
        return field == null ? null : field.dataType();
    }

}
