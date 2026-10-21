package com.ontoweave.data.pipeline.expectation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

public final class ExpectationRules {

    public static final String PRIMARY_KEY_UNIQUE = "PRIMARY_KEY_UNIQUE";
    public static final String ROW_COUNT_RANGE = "ROW_COUNT_RANGE";
    public static final String VALUE_SET = "VALUE_SET";
    public static final String NOT_NULL = "NOT_NULL";

    private ExpectationRules() {
    }

    public static Optional<ObjectNode> normalize(JsonNode rule) {
        if (rule == null || !rule.isObject()) {
            return Optional.empty();
        }
        String type = canonicalType(rule.path("type").asText(null));
        if (type == null) {
            return Optional.empty();
        }
        ObjectNode normalized = ((ObjectNode) rule).deepCopy();
        normalized.put("type", type);
        return Optional.of(normalized);
    }

    public static String canonicalType(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case PRIMARY_KEY_UNIQUE, "primaryKeyUnique" -> PRIMARY_KEY_UNIQUE;
            case ROW_COUNT_RANGE, "rowCount" -> ROW_COUNT_RANGE;
            case VALUE_SET, "acceptedValues" -> VALUE_SET;
            case NOT_NULL, "notNull" -> NOT_NULL;
            default -> null;
        };
    }
}
