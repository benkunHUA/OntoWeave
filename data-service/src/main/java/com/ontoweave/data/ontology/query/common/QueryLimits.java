package com.ontoweave.data.ontology.query.common;

import java.util.LinkedHashSet;
import java.util.List;

public final class QueryLimits {

    public static final int MAX_QUERY_LIMIT = 50;

    private QueryLimits() {
    }

    public static int limit(Integer value) {
        int resolved = value == null ? QueryConstants.DEFAULT_PAGE_SIZE : value;
        if (resolved < 1 || resolved > MAX_QUERY_LIMIT) {
            throw QueryErrors.invalidRequest("limit 必须在 1 到 " + MAX_QUERY_LIMIT + " 之间");
        }
        return resolved;
    }

    public static List<String> primaryKeys(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw QueryErrors.invalidRequest("primaryKeys 不能包含空值");
            }
            unique.add(value);
        }
        if (unique.size() > MAX_QUERY_LIMIT) {
            throw QueryErrors.invalidRequest(
                    "primaryKeys 不能超过 " + MAX_QUERY_LIMIT + " 个"
            );
        }
        return List.copyOf(unique);
    }
}
