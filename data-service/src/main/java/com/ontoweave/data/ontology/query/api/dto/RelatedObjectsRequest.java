package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;
import java.util.UUID;

public record RelatedObjectsRequest(
        UUID ownerId,
        String direction,
        List<ObjectReference> inputs,
        Integer limit
) {

    public RelatedObjectsRequest {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
