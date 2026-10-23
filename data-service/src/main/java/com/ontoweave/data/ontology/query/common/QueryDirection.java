package com.ontoweave.data.ontology.query.common;

import java.util.Locale;

public enum QueryDirection {
    OUTGOING("outgoing"),
    INCOMING("incoming"),
    BOTH("both");

    private final String wireName;

    QueryDirection(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static QueryDirection parse(String value, boolean allowBoth) {
        String normalized = value == null ? "both" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "outgoing" -> OUTGOING;
            case "incoming" -> INCOMING;
            case "both" -> {
                if (!allowBoth) {
                    throw QueryErrors.invalidRequest("该接口的 direction 只支持 outgoing 或 incoming");
                }
                yield BOTH;
            }
            default -> throw QueryErrors.invalidRequest("direction 只支持 outgoing、incoming 或 both");
        };
    }
}
