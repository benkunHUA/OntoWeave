package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;
import java.util.UUID;

public record RelatedObjectPageResponse(
        List<RelatedObjectResponse> items,
        long total,
        int page,
        int pageSize,
        UUID indexVersionId
) {

    public RelatedObjectPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
