package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;
import java.util.UUID;

public record RelationGroupResponse(
        UUID relationId,
        String direction,
        String name,
        long total,
        List<RelatedObjectResponse> relatedObjects
) {

    public RelationGroupResponse {
        relatedObjects = relatedObjects == null
                ? List.of()
                : List.copyOf(relatedObjects);
    }
}
