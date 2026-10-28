package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;
import java.util.UUID;

public record RelatedObjectsResponse(
        List<RelatedObjectRef> items,
        long total,
        boolean truncated,
        UUID indexVersionId
) {

    public RelatedObjectsResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
