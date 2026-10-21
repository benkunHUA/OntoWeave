package com.ontoweave.data.pipeline.execution.build;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBuildRequest(
        long draftRevision,
        @NotNull UUID operationId
) {
}
