package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class OutputValidator {

    void validateReachability(
            Map<UUID, DraftNode> nodes,
            Set<UUID> outputIds,
            Map<UUID, OutputConfiguration> outputConfigurations,
            List<PipelineIssue> issues
    ) {
        List<DraftNode> outputNodes = nodes.values().stream()
                .filter(node -> "output".equals(node.type()))
                .toList();
        if (outputNodes.isEmpty()) {
            issues.add(issue(
                    PipelineIssueCodes.OUTPUT_MISSING,
                    PipelineIssueCodes.PHASE_GRAPH,
                    null,
                    null,
                    "流程没有输出节点",
                    "添加 output 节点"
            ));
            return;
        }
        for (DraftNode output : outputNodes) {
            UUID outputId = boundOutputId(output);
            if (outputId == null || !outputIds.contains(outputId)) {
                bindingIssue(output.id(), "输出节点未绑定有效 dataset 输出", issues);
                continue;
            }
            OutputConfiguration configuration = outputConfigurations.get(outputId);
            if (configuration == null) {
                bindingIssue(output.id(), "输出记录不存在或不可见", issues);
            } else if (configuration.nodeId() != null
                    && !configuration.nodeId().equals(output.id())) {
                bindingIssue(output.id(), "输出记录绑定了其他节点", issues);
            } else if (!configuration.datasetConfigured()) {
                bindingIssue(output.id(), "输出缺少对应的 dataset 记录", issues);
            }
        }
    }

    void validateConfiguration(
            Map<UUID, DraftNode> nodes,
            Map<UUID, List<FieldSchema>> schemas,
            Set<UUID> outputIds,
            Map<UUID, OutputConfiguration> outputConfigurations,
            List<PipelineIssue> issues
    ) {
        for (DraftNode output : nodes.values()) {
            if (!"output".equals(output.type())) {
                continue;
            }
            UUID outputId = boundOutputId(output);
            if (outputId == null || !outputIds.contains(outputId)) {
                continue;
            }
            OutputConfiguration configuration = outputConfigurations.get(outputId);
            if (configuration == null && output.config() != null) {
                configuration = new OutputConfiguration(output.config());
            }
            if (configuration == null) {
                continue;
            }
            if (configuration.nodeId() != null && !configuration.nodeId().equals(output.id())) {
                continue;
            }
            if (!configuration.datasetConfigured()) {
                continue;
            }
            List<FieldSchema> schema = schemas.get(output.id());
            validatePrimaryKeys(output.id().toString(), configuration.primaryKeyColumns(), schema, issues);
            validateExpectations(output.id().toString(), configuration.expectations(), schema, issues);
        }
    }

    private UUID boundOutputId(DraftNode output) {
        return uuid(output.config() == null ? null : output.config().path("outputId").asText(null));
    }

    private void bindingIssue(UUID nodeId, String message, List<PipelineIssue> issues) {
        issues.add(issue(
                PipelineIssueCodes.OUTPUT_MISSING,
                PipelineIssueCodes.PHASE_SCHEMA,
                nodeId.toString(),
                null,
                message,
                "执行 set_output 重新绑定输出"
        ));
    }

    private void validatePrimaryKeys(
            String nodeId,
            JsonNode primaryKeys,
            List<FieldSchema> schema,
            List<PipelineIssue> issues
    ) {
        if (primaryKeys == null || !primaryKeys.isArray()) {
            primaryKeyIssue(nodeId, null, "主键必须是数组", issues);
            return;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode value : primaryKeys) {
            if (!value.isTextual() || value.asText().isBlank()) {
                primaryKeyIssue(nodeId, null, "主键字段必须是非空字符串", issues);
                continue;
            }
            String field = value.asText();
            if (!seen.add(field)) {
                primaryKeyIssue(nodeId, field, "主键字段重复: " + field, issues);
            }
            if (schema != null && field(schema, field) == null) {
                primaryKeyIssue(nodeId, field, "主键字段不存在: " + field, issues);
            }
        }
    }

    private void validateExpectations(
            String nodeId,
            JsonNode expectations,
            List<FieldSchema> schema,
            List<PipelineIssue> issues
    ) {
        if (expectations == null || !expectations.isArray()) {
            expectationIssue(nodeId, null, "expectations 必须是数组", issues);
            return;
        }
        for (JsonNode expectation : expectations) {
            if (!expectation.isObject()) {
                expectationIssue(nodeId, null, "expectation 必须是对象", issues);
                continue;
            }
            String type = expectation.path("type").asText(null);
            if (type == null) {
                expectationIssue(nodeId, null, "expectation.type 不能为空", issues);
                continue;
            }
            switch (type) {
                case "rowCount" -> validateRowCount(nodeId, expectation, issues);
                case "acceptedValues" -> validateAcceptedValues(nodeId, expectation, schema, issues);
                case "notNull" -> validateNotNull(nodeId, expectation, schema, issues);
                default -> expectationIssue(nodeId, "type", "不支持的 expectation 类型: " + type, issues);
            }
        }
    }

    private void validateRowCount(String nodeId, JsonNode expectation, List<PipelineIssue> issues) {
        JsonNode minNode = expectation.get("min");
        JsonNode maxNode = expectation.get("max");
        if (minNode == null && maxNode == null) {
            expectationIssue(nodeId, null, "rowCount 至少需要 min 或 max", issues);
            return;
        }
        Long min = nonNegativeLong(minNode);
        Long max = nonNegativeLong(maxNode);
        if ((minNode != null && min == null) || (maxNode != null && max == null)) {
            expectationIssue(nodeId, minNode != null && min == null ? "min" : "max", "rowCount 边界必须是非负整数", issues);
            return;
        }
        if (min != null && max != null && min > max) {
            expectationIssue(nodeId, "min", "rowCount min 不能大于 max", issues);
        }
    }

    private void validateAcceptedValues(
            String nodeId,
            JsonNode expectation,
            List<FieldSchema> schema,
            List<PipelineIssue> issues
    ) {
        String column = expectation.path("column").asText(null);
        JsonNode values = expectation.path("values");
        if (column == null || column.isBlank()) {
            expectationIssue(nodeId, "column", "acceptedValues.column 不能为空", issues);
        } else if (schema != null && field(schema, column) == null) {
            expectationIssue(nodeId, "column", "期望字段不存在: " + column, issues);
        }
        if (!values.isArray() || values.isEmpty()) {
            expectationIssue(nodeId, "values", "acceptedValues.values 不能为空", issues);
        }
    }

    private void validateNotNull(
            String nodeId,
            JsonNode expectation,
            List<FieldSchema> schema,
            List<PipelineIssue> issues
    ) {
        JsonNode columns = expectation.path("columns");
        if (!columns.isArray() || columns.isEmpty()) {
            expectationIssue(nodeId, "columns", "notNull.columns 不能为空", issues);
            return;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode value : columns) {
            if (!value.isTextual() || value.asText().isBlank()) {
                expectationIssue(nodeId, "columns", "notNull 字段必须是非空字符串", issues);
                continue;
            }
            String column = value.asText();
            if (!seen.add(column)) {
                expectationIssue(nodeId, "columns", "notNull 字段重复: " + column, issues);
            }
            if (schema != null && field(schema, column) == null) {
                expectationIssue(nodeId, "columns", "期望字段不存在: " + column, issues);
            }
        }
    }

    private Long nonNegativeLong(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            return null;
        }
        long number = value.asLong();
        return number < 0 ? null : number;
    }

    private FieldSchema field(List<FieldSchema> fields, String name) {
        return fields.stream().filter(field -> field.name().equals(name)).findFirst().orElse(null);
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private void primaryKeyIssue(
            String nodeId,
            String field,
            String message,
            List<PipelineIssue> issues
    ) {
        issues.add(issue(
                PipelineIssueCodes.PRIMARY_KEY_INVALID,
                PipelineIssueCodes.PHASE_SCHEMA,
                nodeId,
                field,
                message,
                "修正主键配置"
        ));
    }

    private void expectationIssue(
            String nodeId,
            String field,
            String message,
            List<PipelineIssue> issues
    ) {
        issues.add(issue(
                PipelineIssueCodes.EXPECTATION_INVALID,
                PipelineIssueCodes.PHASE_SCHEMA,
                nodeId,
                field,
                message,
                "修正数据期望配置"
        ));
    }

    private PipelineIssue issue(
            String code,
            String phase,
            String nodeId,
            String field,
            String message,
            String hint
    ) {
        return new PipelineIssue(code, phase, true, nodeId, null, field, message, hint);
    }
}
