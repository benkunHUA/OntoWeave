package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class OutputNodeIds {

    private static final String OUTPUT_TYPE = "output";

    private OutputNodeIds() {
    }

    public static Set<UUID> from(JsonNode definition) {
        return PipelineDraftGraph.from(definition).nodes().stream()
                .filter(node -> OUTPUT_TYPE.equals(node.type()))
                .map(DraftNode::id)
                .collect(Collectors.toUnmodifiableSet());
    }
}
