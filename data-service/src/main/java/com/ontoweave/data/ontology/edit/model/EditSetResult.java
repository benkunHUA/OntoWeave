package com.ontoweave.data.ontology.edit.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public record EditSetResult(
        UUID executionId,
        List<ObjectResult> objects,
        List<RelationResult> relations
) {

    public EditSetResult {
        objects = objects == null ? List.of() : List.copyOf(objects);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    public record ObjectResult(
            UUID objectTypeId,
            String primaryKey,
            boolean exists,
            String title,
            JsonNode properties,
            long version
    ) {
    }

    public record RelationResult(
            UUID relationId,
            UUID fromObjectTypeId,
            String fromPrimaryKey,
            UUID toObjectTypeId,
            String toPrimaryKey,
            boolean exists
    ) {
    }
}
