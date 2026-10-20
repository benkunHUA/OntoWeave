package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record MoveNodeCommand(UUID nodeId, JsonNode position) {
}
