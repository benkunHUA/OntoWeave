package com.ontoweave.data.pipeline.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PreviewRequest(
        long draftRevision,
        @NotNull String nodeId,
        @NotNull UUID requestId
) {
}
