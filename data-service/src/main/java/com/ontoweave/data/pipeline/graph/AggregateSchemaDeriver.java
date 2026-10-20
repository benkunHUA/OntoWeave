package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AggregateSchemaDeriver extends SchemaDeriverSupport {

    AggregateSchemaDeriver(String nodeId, List<PipelineIssue> issues) {
        super(nodeId, issues);
    }

    List<FieldSchema> aggregate(JsonNode config, List<FieldSchema> input) {
        JsonNode groupBy = config.path("groupBy");
        if (!groupBy.isArray()) {
            incomplete("groupBy");
            return null;
        }
        JsonNode aggregations = config.path("aggregations");
        if (!aggregations.isArray()) {
            incomplete("aggregations");
            return null;
        }
        if (input == null) {
            return null;
        }
        List<FieldSchema> output = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode group : groupBy) {
            String field = group.asText(null);
            if (field == null || field.isBlank()) {
                incomplete("groupBy");
                continue;
            }
            FieldSchema source = field(input, field);
            if (source == null) {
                fieldNotFound(field);
            } else if (!names.add(field)) {
                duplicate(field);
            } else {
                output.add(source);
            }
        }
        for (JsonNode aggregation : aggregations) {
            aggregateOne(aggregation, input, output, names);
        }
        return output;
    }

    private void aggregateOne(
            JsonNode aggregation,
            List<FieldSchema> input,
            List<FieldSchema> output,
            Set<String> names
    ) {
        String op = aggregation.path("op").asText(null);
        String outputField = aggregation.path("outputField").asText(null);
        String field = aggregation.path("field").asText(null);
        if (op == null || outputField == null || outputField.isBlank()) {
            incomplete("aggregations");
            return;
        }
        if (!names.add(outputField)) {
            duplicate(outputField);
            return;
        }
        switch (op) {
            case "count" -> {
                if (field != null && !field.isBlank() && field(input, field) == null) {
                    fieldNotFound(field);
                }
                output.add(new FieldSchema(outputField, "long", false));
            }
            case "sum" -> {
                FieldSchema source = requireField(input, field);
                if (source == null) {
                    return;
                }
                if (!ExpressionTypeResolver.numeric(source.dataType())) {
                    invalidAggregation(field, "sum 只能作用于数值字段");
                } else {
                    output.add(new FieldSchema(outputField, sumType(source.dataType()), false));
                }
            }
            case "avg" -> {
                FieldSchema source = requireField(input, field);
                if (source == null) {
                    return;
                }
                if (!ExpressionTypeResolver.numeric(source.dataType())) {
                    invalidAggregation(field, "avg 只能作用于数值字段");
                } else {
                    output.add(new FieldSchema(outputField, "double", false));
                }
            }
            case "min", "max" -> {
                FieldSchema source = requireField(input, field);
                if (source != null) {
                    output.add(new FieldSchema(outputField, source.dataType(), false));
                }
            }
            default -> invalidAggregation(field, "未知聚合操作: " + op);
        }
    }

    private FieldSchema requireField(List<FieldSchema> input, String field) {
        if (field == null || field.isBlank()) {
            incomplete("aggregations");
            return null;
        }
        FieldSchema source = field(input, field);
        if (source == null) {
            fieldNotFound(field);
        }
        return source;
    }

    private String sumType(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("double") || normalized.contains("float")) {
            return "double";
        }
        if (normalized.contains("decimal")) {
            return type;
        }
        return "long";
    }

    private void invalidAggregation(String field, String message) {
        issues.add(issue(PipelineIssueCodes.AGGREGATION_INVALID, field, message, "修正聚合配置"));
    }

    private void duplicate(String field) {
        issues.add(issue(
                PipelineIssueCodes.DUPLICATE_FIELD,
                field,
                "聚合输出字段重复: " + field,
                "使用唯一输出字段名"
        ));
    }
}
