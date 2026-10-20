package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record DraftNode(UUID id, String type, String name, JsonNode config, JsonNode position) {
}
