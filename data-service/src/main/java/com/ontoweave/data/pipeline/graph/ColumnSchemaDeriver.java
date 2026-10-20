package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ontoweave.data.pipeline.graph.SchemaValues.text;

final class ColumnSchemaDeriver {

    List<FieldSchema> select(
            DraftNode node,
            JsonNode config,
            List<FieldSchema> input,
            List<PipelineIssue> issues
    ) {
        String mode = text(config, "mode");
        JsonNode columns = config.path("columns");
        if (mode == null || !columns.isArray()) {
            incomplete(node.id().toString(), null, "columns", issues);
            return null;
        }
        if (input == null) {
            return null;
        }
        List<String> names = strings(columns);
        if ("include".equals(mode)) {
            List<FieldSchema> output = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String name : names) {
                FieldSchema field = field(input, name);
                if (field == null) {
                    fieldNotFound(node.id().toString(), name, issues);
                } else if (!seen.add(name)) {
                    duplicate(node.id().toString(), name, issues);
                } else {
                    output.add(field);
                }
            }
            return output;
        }
        if ("exclude".equals(mode)) {
            Set<String> namesToRemove = new HashSet<>(names);
            for (String name : names) {
                if (field(input, name) == null) {
                    fieldNotFound(node.id().toString(), name, issues);
                }
            }
            return input.stream().filter(field -> !namesToRemove.contains(field.name())).toList();
        }
        incomplete(node.id().toString(), null, "mode", issues);
        return null;
    }

    List<FieldSchema> rename(
            DraftNode node,
            JsonNode config,
            List<FieldSchema> input,
            List<PipelineIssue> issues
    ) {
        JsonNode renames = config.path("renames");
        if (!renames.isArray()) {
            incomplete(node.id().toString(), null, "renames", issues);
            return null;
        }
        if (input == null) {
            return null;
        }
        Map<String, String> mapping = new HashMap<>();
        for (JsonNode rename : renames) {
            String from = rename.path("from").asText(null);
            String to = rename.path("to").asText(null);
            if (from == null || from.isBlank() || to == null || to.isBlank()) {
                incomplete(node.id().toString(), null, "renames", issues);
                continue;
            }
            if (field(input, from) == null) {
                fieldNotFound(node.id().toString(), from, issues);
                continue;
            }
            mapping.put(from, to);
        }
        List<FieldSchema> output = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FieldSchema field : input) {
            String name = mapping.getOrDefault(field.name(), field.name());
            if (!seen.add(name)) {
                duplicate(node.id().toString(), name, issues);
                continue;
            }
            output.add(new FieldSchema(name, field.dataType(), field.nullable()));
        }
        return output;
    }

    List<FieldSchema> deriveColumn(
            DraftNode node,
            JsonNode config,
            List<FieldSchema> input,
            List<PipelineIssue> issues
    ) {
        String outputField = text(config, "outputField");
        JsonNode expression = config.path("expression");
        if (outputField == null || !expression.isObject()) {
            incomplete(node.id().toString(), null, "outputField", issues);
            return null;
        }
        if (input == null) {
            return null;
        }
        String type = new ExpressionTypeResolver(input, node.id().toString(), issues)
                .type(expression)
                .orElse(null);
        if (type == null) {
            return null;
        }
        List<FieldSchema> output = new ArrayList<>(input);
        FieldSchema derived = new FieldSchema(outputField, type, true);
        boolean replaced = false;
        for (int index = 0; index < output.size(); index++) {
            if (output.get(index).name().equals(outputField)) {
                output.set(index, derived);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            output.add(derived);
        }
        return output;
    }

    List<FieldSchema> deduplicate(
            DraftNode node,
            JsonNode config,
            List<FieldSchema> input,
            List<PipelineIssue> issues
    ) {
        String mode = text(config, "mode");
        JsonNode columns = config.path("columns");
        if ("subset".equals(mode) && !columns.isArray()) {
            incomplete(node.id().toString(), null, "columns", issues);
            return null;
        }
        if (mode == null) {
            incomplete(node.id().toString(), null, "mode", issues);
            return null;
        }
        if (input == null) {
            return null;
        }
        if ("all".equals(mode)) {
            return input;
        }
        if (!"subset".equals(mode)) {
            incomplete(node.id().toString(), null, "mode", issues);
            return null;
        }
        for (String name : strings(columns)) {
            if (field(input, name) == null) {
                fieldNotFound(node.id().toString(), name, issues);
            }
        }
        return input;
    }

    private FieldSchema field(List<FieldSchema> fields, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return fields.stream().filter(field -> field.name().equals(name)).findFirst().orElse(null);
    }

    private List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private void fieldNotFound(String nodeId, String field, List<PipelineIssue> issues) {
        issues.add(new PipelineIssue(
                PipelineIssueCodes.FIELD_NOT_FOUND,
                PipelineIssueCodes.PHASE_SCHEMA,
                true,
                nodeId,
                null,
                field,
                "字段不存在: " + field,
                "选择上游已有字段"
        ));
    }

    private void duplicate(String nodeId, String field, List<PipelineIssue> issues) {
        issues.add(new PipelineIssue(
                PipelineIssueCodes.DUPLICATE_FIELD,
                PipelineIssueCodes.PHASE_SCHEMA,
                true,
                nodeId,
                null,
                field,
                "字段重名: " + field,
                "使用唯一字段名"
        ));
    }

    private void incomplete(String nodeId, String portId, String field, List<PipelineIssue> issues) {
        issues.add(new PipelineIssue(
                PipelineIssueCodes.NODE_CONFIG_INCOMPLETE,
                PipelineIssueCodes.PHASE_SCHEMA,
                true,
                nodeId,
                portId,
                field,
                "节点配置不完整: " + field,
                "补全节点配置"
        ));
    }
}
