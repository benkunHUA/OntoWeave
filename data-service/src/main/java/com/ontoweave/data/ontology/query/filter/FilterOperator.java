package com.ontoweave.data.ontology.query.filter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum FilterOperator {
    EQ("eq"),
    NE("ne"),
    CONTAINS("contains"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    IS_NULL("is_null"),
    IS_NOT_NULL("is_not_null");

    private final String wireName;

    FilterOperator(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public boolean isUnary() {
        return this == IS_NULL || this == IS_NOT_NULL;
    }

    public static Optional<FilterOperator> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(operator -> operator.wireName.equals(normalized))
                .findFirst();
    }
}
