package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PipelineFrozenOutput(
        UUID outputId,
        UUID nodeId,
        PipelineOutputType outputType,
        String name,
        String writeMode,
        JsonNode primaryKeyColumns,
        JsonNode expectations
) {

    public static List<PipelineFrozenOutput> from(JsonNode outputs, ObjectMapper mapper) {
        List<PipelineFrozenOutput> parsed = new ArrayList<>();
        if (outputs == null || !outputs.isArray()) {
            return List.of();
        }
        outputs.forEach(item -> parsed.add(new PipelineFrozenOutput(
                uuid(item.path("outputId").asText(null)),
                uuid(item.path("nodeId").asText(null)),
                outputType(item.path("outputType").asText(null)),
                item.path("name").asText(null),
                item.path("writeMode").asText("snapshot"),
                array(item.get("primaryKeyColumns"), mapper),
                array(item.get("expectations"), mapper)
        )));
        return List.copyOf(parsed);
    }

    private static PipelineOutputType outputType(String value) {
        try {
            return PipelineOutputType.valueOf(value == null ? "DATASET" : value.toUpperCase());
        } catch (IllegalArgumentException error) {
            return PipelineOutputType.DATASET;
        }
    }

    private static JsonNode array(JsonNode value, ObjectMapper mapper) {
        return value != null && value.isArray() ? value.deepCopy() : mapper.createArrayNode();
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
