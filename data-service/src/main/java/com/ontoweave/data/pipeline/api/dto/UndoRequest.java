package com.ontoweave.data.pipeline.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UndoRequest(@NotNull @Min(0) Long currentRevision) {
}
