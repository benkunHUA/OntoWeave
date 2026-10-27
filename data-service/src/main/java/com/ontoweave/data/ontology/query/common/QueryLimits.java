package com.ontoweave.data.ontology.query.common;

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
}
