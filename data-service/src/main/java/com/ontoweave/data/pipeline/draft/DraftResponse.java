package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record DraftResponse(UUID pipelineId, long revision, JsonNode definition) {

    public DraftResponse {
        definition = definition == null ? null : definition.deepCopy();
    }

    @Override
    public JsonNode definition() {
        return definition == null ? null : definition.deepCopy();
    }
}
