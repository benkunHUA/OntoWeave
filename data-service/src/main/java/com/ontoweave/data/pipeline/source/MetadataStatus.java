package com.ontoweave.data.pipeline.source;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum MetadataStatus {
    AVAILABLE,
    MISSING,
    RESTRICTED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
