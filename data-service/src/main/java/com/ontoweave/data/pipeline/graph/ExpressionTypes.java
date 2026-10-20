package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

final class ExpressionTypes {

    private ExpressionTypes() {
    }

    static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    static boolean literalMatches(String dataType, JsonNode value) {
        String normalized = normalize(dataType);
        return switch (normalized) {
            case "boolean" -> value.isBoolean();
            case "string", "date", "timestamp" -> value.isTextual();
            case "integer", "int", "short", "long" -> value.isIntegralNumber();
            case "float", "double", "decimal", "number" -> value.isNumber();
            default -> true;
        };
    }

    static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
