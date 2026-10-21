package com.ontoweave.data.pipeline.api.dto;

import jakarta.validation.constraints.Min;

public record UndoRequest(@Min(0) long currentRevision) {
}
