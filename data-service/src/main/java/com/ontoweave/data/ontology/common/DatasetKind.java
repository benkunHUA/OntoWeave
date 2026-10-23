package com.ontoweave.data.ontology.common;

import java.util.Locale;

public enum DatasetKind {
    SYNC,
    PIPELINE;

    public static DatasetKind from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("dataset kind is required");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
