package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.source.DisplaySnapshot;

import java.util.List;
import java.util.UUID;

public final class PipelineResolvedInputs {

    private PipelineResolvedInputs() {
    }

    public static JsonNode toJson(ObjectMapper mapper, List<ResolvedInput> inputs) {
        return mapper.valueToTree(inputs == null ? List.of() : inputs);
    }

    public static List<ResolvedInput> fromJson(ObjectMapper mapper, JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        try {
            return mapper.convertValue(node, new TypeReference<List<ResolvedInput>>() {
            });
        } catch (IllegalArgumentException error) {
            return List.of();
        }
    }

    public static ResolvedInput copy(ResolvedInput input) {
        DisplaySnapshot display = input.displaySnapshot();
        return new ResolvedInput(
                input.datasetKind(),
                input.datasetId(),
                input.datasetVersionId(),
                input.datasetVersionNumber(),
                input.schemaHash(),
                input.icebergCatalog(),
                input.icebergNamespace(),
                input.icebergTable(),
                input.icebergSnapshotId(),
                display == null ? null : new DisplaySnapshot(
                        display.datasetName(),
                        display.datasetVersionNumber(),
                        display.connectionId(),
                        display.connectionName(),
                        display.sourceSchemaName(),
                        display.sourceTableName()
                )
        );
    }

    public static UUID datasetVersionId(ResolvedInput input) {
        return input == null ? null : input.datasetVersionId();
    }
}
