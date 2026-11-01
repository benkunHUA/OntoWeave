package com.ontoweave.data.ontology.edit.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EditSetPreview(
        UUID executionId,
        List<ObjectChange> objects,
        List<RelationChange> relations,
        List<Conflict> conflicts,
        boolean applicable
) {

    public EditSetPreview {
        objects = objects == null ? List.of() : List.copyOf(objects);
        relations = relations == null ? List.of() : List.copyOf(relations);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public record ObjectState(
            String primaryKey,
            String title,
            JsonNode properties,
            long version
    ) {
    }

    public record ObjectChange(
            String kind,
            UUID objectTypeId,
            String primaryKey,
            ObjectState before,
            ObjectState after
    ) {
    }

    public record RelationChange(
            String kind,
            UUID relationId,
            UUID fromObjectTypeId,
            String fromPrimaryKey,
            UUID toObjectTypeId,
            String toPrimaryKey,
            boolean beforeExists,
            boolean afterExists,
            long beforeVersion
    ) {
    }

    public record Conflict(String code, String message, Map<String, Object> details) {

        public Conflict {
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }
}
