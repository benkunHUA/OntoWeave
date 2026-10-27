package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;
import java.util.UUID;

public record QueryPageResponse(
        List<QueryObjectResponse> items,
        long total,
        boolean truncated,
        UUID indexVersionId
) {

    public QueryPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
