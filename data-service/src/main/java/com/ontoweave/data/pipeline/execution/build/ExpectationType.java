package com.ontoweave.data.pipeline.execution.build;

import java.util.Locale;

public enum ExpectationType {
    PRIMARY_KEY_UNIQUE,
    ROW_COUNT_RANGE,
    VALUE_SET,
    NOT_NULL;

    public static ExpectationType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("expectation type is required");
        }
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
