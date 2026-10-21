package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ontoweave.data.pipeline.graph.SchemaValues.text;

final class NodeSchemaDeriver {

    private final ColumnSchemaDeriver columns = new ColumnSchemaDeriver();

    List<FieldSchema> derive(
            DraftNode node,
            Inputs inputs,
            Map<UUID, InputSchema> inputSchemas,
            List<PipelineIssue> issues
    ) {
        JsonNode config = node.config();
        if (config == null) {
            incomplete(node.id().toString(), null, "config", issues);
            return null;
        }
        return switch (node.type()) {
            case "input" -> input(node, config, inputSchemas, issues);
            case "filter" -> filter(node, config, inputs, issues);
            case "select_columns" -> columns.select(node, config, inputs.single("in"), issues);
            case "rename_columns" -> columns.rename(node, config, inputs.single("in"), issues);
            case "derive_column" -> columns.deriveColumn(node, config, inputs.single("in"), issues);
            case "deduplicate" -> columns.deduplicate(node, config, inputs.single("in"), issues);
            case "join" -> new RelationalSchemaDeriver(node.id().toString(), issues).join(
                    config,
                    inputs.single("left"),
                    inputs.single("right")
            );
            case "union" -> new RelationalSchemaDeriver(node.id().toString(), issues).union(
                    inputs.all("inputs")
            );
            case "aggregate" -> new AggregateSchemaDeriver(node.id().toString(), issues).aggregate(
                    config,
                    inputs.single("in")
            );
            case "output" -> inputs.single("in");
            default -> null;
        };
    }

    private List<FieldSchema> input(
            DraftNode node,
            JsonNode config,
            Map<UUID, InputSchema> inputSchemas,
            List<PipelineIssue> issues
    ) {
        String datasetKind = text(config, "datasetKind");
        String datasetIdText = text(config, "datasetId");
        if (datasetKind == null || datasetIdText == null) {
            incomplete(node.id().toString(), null, "datasetId", issues);
            return null;
        }
        UUID datasetId;
        try {
            datasetId = UUID.fromString(datasetIdText);
        } catch (IllegalArgumentException error) {
            incomplete(node.id().toString(), null, "datasetId", issues);
            return null;
        }
        InputSchema schema = inputSchemas.get(datasetId);
        if (schema == null) {
            issues.add(new PipelineIssue(
                    PipelineIssueCodes.INPUT_VERSION_UNRESOLVED,
                    PipelineIssueCodes.PHASE_SCHEMA,
                    true,
                    node.id().toString(),
                    null,
                    null,
                    "输入数据集没有已发布版本: " + datasetKind + "/" + datasetId,
                    "先发布输入数据集"
            ));
            return null;
        }
        FieldNameRules.rejectReservedFields(node.id().toString(), schema.fields(), issues);
        return schema.fields();
    }

    private List<FieldSchema> filter(
            DraftNode node,
            JsonNode config,
            Inputs inputs,
            List<PipelineIssue> issues
    ) {
        JsonNode condition = config.path("condition");
        if (!condition.isObject()) {
            incomplete(node.id().toString(), null, "condition", issues);
            return null;
        }
        List<FieldSchema> input = inputs.single("in");
        if (input == null) {
            return null;
        }
        new ExpressionTypeResolver(input, node.id().toString(), issues).validateCondition(condition);
        return input;
    }

    private void incomplete(
            String nodeId,
            String portId,
            String field,
            List<PipelineIssue> issues
    ) {
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

    record Inputs(Map<String, List<List<FieldSchema>>> byPort) {

        List<FieldSchema> single(String port) {
            List<List<FieldSchema>> schemas = byPort.getOrDefault(port, List.of());
            return schemas.isEmpty() ? null : schemas.get(0);
        }

        List<List<FieldSchema>> all(String port) {
            return byPort.getOrDefault(port, List.of());
        }
    }
}
