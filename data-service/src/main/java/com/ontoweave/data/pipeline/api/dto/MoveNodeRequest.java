package com.ontoweave.data.pipeline.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveNodeRequest(@NotNull UUID nodeId, @NotNull JsonNode position) {
}
