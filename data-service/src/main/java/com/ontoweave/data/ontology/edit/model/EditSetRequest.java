package com.ontoweave.data.ontology.edit.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EditSetRequest(
        UUID executionId,
        UUID ownerId,
        UUID conversationId,
        UUID graphId,
        UUID actionTypeId,
        String actionDefinitionHash,
        JsonNode actionSnapshot,
        JsonNode parameters,
        String idempotencyKey,
        Expectations expected,
        List<ObjectCreate> objectCreates,
        List<ObjectUpdate> objectUpdates,
        List<ObjectDelete> objectDeletes,
        List<RelationCreate> relationCreates,
        List<RelationDelete> relationDeletes
) {

    public EditSetRequest {
        expected = expected == null ? Expectations.empty() : expected;
        objectCreates = list(objectCreates);
        objectUpdates = list(objectUpdates);
        objectDeletes = list(objectDeletes);
        relationCreates = list(relationCreates);
        relationDeletes = list(relationDeletes);
    }

    public record Expectations(
            List<ObjectExpectation> objects,
            List<RelationExpectation> relations
    ) {

        public Expectations {
            objects = list(objects);
            relations = list(relations);
        }

        public static Expectations empty() {
            return new Expectations(List.of(), List.of());
        }
    }

    public record ObjectExpectation(
            UUID objectTypeId,
            String primaryKey,
            long version
    ) {
    }

    public record RelationExpectation(
            UUID relationId,
            String fromPrimaryKey,
            String toPrimaryKey,
            long version
    ) {
    }

    public record ObjectCreate(
            UUID objectTypeId,
            String primaryKey,
            Map<UUID, JsonNode> values
    ) {

        public ObjectCreate {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record ObjectUpdate(
            UUID objectTypeId,
            String primaryKey,
            Map<UUID, JsonNode> set,
            List<UUID> clear
    ) {

        public ObjectUpdate {
            set = set == null ? Map.of() : Map.copyOf(set);
            clear = list(clear);
        }
    }

    public record ObjectDelete(UUID objectTypeId, String primaryKey) {
    }

    public record RelationCreate(
            UUID relationId,
            String fromPrimaryKey,
            String toPrimaryKey
    ) {
    }

    public record RelationDelete(
            UUID relationId,
            String fromPrimaryKey,
            String toPrimaryKey
    ) {
    }

    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
