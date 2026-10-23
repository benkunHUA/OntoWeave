package com.ontoweave.data.ontology.binding.support;

import java.util.Locale;

public final class BindingTypeRules {

    private BindingTypeRules() {
    }

    public static boolean foreignKeyCompatible(String foreignKeyDatatype, String targetKeyDatatype) {
        return normalize(foreignKeyDatatype).equals(normalize(targetKeyDatatype));
    }

    private static String normalize(String type) {
        if (type == null) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        int parenthesis = normalized.indexOf('(');
        if (parenthesis >= 0) {
            normalized = normalized.substring(0, parenthesis).trim();
        }
        return switch (normalized) {
            case "timestamp with time zone", "timestamp_tz", "timestamp with zone", "timestamptz" ->
                    "timestamp";
            case "timestamp without time zone", "timestamp_ntz", "timestamp without zone" ->
                    "timestamp";
            default -> normalized;
        };
    }
}
