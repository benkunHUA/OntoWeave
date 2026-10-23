package com.ontoweave.data.ontology.mapping;

import java.util.Locale;
import java.util.Set;

final class OntologyTypeRules {

    private static final Set<String> PRIMARY_KEY_TYPES = Set.of(
            "text", "integer", "decimal", "date", "timestamp", "timestamptz"
    );

    private OntologyTypeRules() {
    }

    static boolean isSupportedPrimaryKey(String datatype) {
        return PRIMARY_KEY_TYPES.contains(normalize(datatype));
    }

    static boolean sameType(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    static boolean compatible(String attributeType, String datasetType) {
        String attribute = normalize(attributeType);
        String dataset = normalize(datasetType);
        return switch (attribute) {
            case "text" -> dataset.equals("string") || dataset.equals("uuid");
            case "integer" -> dataset.equals("int") || dataset.equals("long");
            case "decimal" -> dataset.equals("decimal")
                    || dataset.equals("int")
                    || dataset.equals("long");
            case "boolean" -> dataset.equals("boolean");
            case "date" -> dataset.equals("date");
            case "timestamp", "timestamptz" ->
                    dataset.equals("timestamp") || dataset.equals("timestamptz");
            case "enum" -> dataset.equals("string");
            default -> false;
        };
    }

    static boolean isNested(String datasetType) {
        String normalized = normalize(datasetType);
        return normalized.startsWith("list")
                || normalized.startsWith("array")
                || normalized.startsWith("struct")
                || normalized.startsWith("map");
    }

    static String normalize(String type) {
        if (type == null) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        int parenthesis = normalized.indexOf('(');
        if (parenthesis >= 0) {
            normalized = normalized.substring(0, parenthesis).trim();
        }
        return switch (normalized) {
            case "timestamp with time zone", "timestamp_tz", "timestamp with zone" ->
                    "timestamptz";
            case "timestamp without time zone", "timestamp_ntz", "timestamp without zone" ->
                    "timestamp";
            default -> normalized;
        };
    }
}
