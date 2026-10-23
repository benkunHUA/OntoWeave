package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;

public record RelationInstancePageResponse(
        List<RelationInstanceResponse> items,
        long total,
        int page,
        int pageSize
) {

    public RelationInstancePageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
