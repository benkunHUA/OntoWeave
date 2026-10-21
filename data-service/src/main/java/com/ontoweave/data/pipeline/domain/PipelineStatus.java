package com.ontoweave.data.pipeline.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PipelineStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
