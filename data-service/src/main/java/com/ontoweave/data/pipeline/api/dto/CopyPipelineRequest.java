package com.ontoweave.data.pipeline.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CopyPipelineRequest(
        @NotNull UUID pipelineId,
        @NotNull UUID conversationId,
        String name
) {
}
