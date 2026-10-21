package com.ontoweave.data.pipeline.service;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePipelineRequest(
        @NotNull UUID ownerId,
        @NotNull UUID pipelineId,
        @NotNull UUID conversationId,
        String name
) {
}
