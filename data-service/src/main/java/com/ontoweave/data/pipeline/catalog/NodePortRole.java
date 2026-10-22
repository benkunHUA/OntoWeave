package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum NodePortRole {
    INPUT,
    OUTPUT;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
