package com.ontoweave.data.pipeline.check;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PipelineStage {
    EMPTY,
    NEEDS_INPUT,
    NEEDS_OUTPUT,
    INVALID,
    READY,
    BUILDING,
    FAILED,
    SUCCEEDED,
    STALE;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
