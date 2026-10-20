package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.check.PipelineIssue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

abstract class PipelineGraphValidatorTestSupport {

    protected static final UUID INPUT_A = UUID.fromString("10000000-0000-4000-8000-000000000001");
    protected static final UUID INPUT_B = UUID.fromString("10000000-0000-4000-8000-000000000002");
    protected static final UUID FILTER = UUID.fromString("20000000-0000-4000-8000-000000000001");
    protected static final UUID FILTER_TWO = UUID.fromString("20000000-0000-4000-8000-000000000002");
    protected static final UUID UNION = UUID.fromString("30000000-0000-4000-8000-000000000001");
    protected static final UUID AGGREGATE = UUID.fromString("30000000-0000-4000-8000-000000000002");
    protected static final UUID OUTPUT = UUID.fromString("40000000-0000-4000-8000-000000000001");
    protected static final UUID OUTPUT_TWO = UUID.fromString("40000000-0000-4000-8000-000000000002");
    protected static final UUID OUTPUT_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final PipelineNodeCatalog catalog = new PipelineNodeCatalog();
    protected final PipelineGraphValidator validator = new PipelineGraphValidator();

    protected List<PipelineIssue> validate(ObjectNode draft, Map<UUID, InputSchema> schemas) {
        return validator.validate(PipelineDraftGraph.from(draft), catalog, schemas, Set.of());
    }

    protected List<String> codes(List<PipelineIssue> issues) {
        return issues.stream().map(PipelineIssue::code).toList();
    }

    protected ObjectNode outputDraft(UUID datasetId) {
        return draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(OUTPUT, "output", outputConfig(OUTPUT_ID))),
                mapper.createArrayNode().add(edge(INPUT_A, "out", OUTPUT, "in"))
        );
    }

    protected ObjectNode emptyDraft() {
        return draft(mapper.createArrayNode(), mapper.createArrayNode());
    }

    protected ObjectNode draft(ArrayNode nodes, ArrayNode edges) {
        ObjectNode draft = mapper.createObjectNode();
        draft.put("schemaVersion", 1);
        draft.set("nodes", nodes);
        draft.set("edges", edges);
        return draft;
    }

    protected ObjectNode node(UUID id, String type, ObjectNode config) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", id.toString());
        node.put("type", type);
        node.put("name", type + "-" + id);
        node.set("config", config);
        ObjectNode position = node.putObject("position");
        position.put("x", 10);
        position.put("y", 20);
        return node;
    }

    protected ObjectNode edge(UUID source, String sourcePort, UUID target, String targetPort) {
        ObjectNode edge = mapper.createObjectNode();
        edge.put("id", UUID.randomUUID().toString());
        edge.put("sourceNodeId", source.toString());
        edge.put("sourcePort", sourcePort);
        edge.put("targetNodeId", target.toString());
        edge.put("targetPort", targetPort);
        return edge;
    }

    protected ObjectNode inputConfig(UUID datasetId) {
        ObjectNode config = mapper.createObjectNode();
        config.put("datasetKind", "sync");
        config.put("datasetId", datasetId.toString());
        return config;
    }

    protected ObjectNode filterConfig(String field, String dataType) {
        try {
            return (ObjectNode) mapper.readTree("""
                    {"condition":{"kind":"compare","field":"%s","op":"gt",
                     "value":{"kind":"literal","dataType":"%s","value":10}}}
                    """.formatted(field, dataType));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    protected ObjectNode outputConfig(UUID outputId) {
        ObjectNode config = mapper.createObjectNode();
        config.put("outputId", outputId.toString());
        return config;
    }

    protected InputSchema schema(String name, String type) {
        return new InputSchema(UUID.randomUUID(), List.of(new FieldSchema(name, type, true)));
    }
}
