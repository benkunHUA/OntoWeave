package com.ontoweave.data.ontology.edit;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;

import java.util.List;
import java.util.UUID;

abstract class EditSetFixtureSupport extends EditOverlayFixtureSupport {

    protected EditSetRequest editSetRequest(
            UUID ownerId,
            String idempotencyKey,
            UUID executionId,
            List<EditSetRequest.ObjectCreate> creates,
            List<EditSetRequest.ObjectUpdate> updates,
            List<EditSetRequest.ObjectDelete> deletes,
            List<EditSetRequest.RelationCreate> relationCreates,
            List<EditSetRequest.RelationDelete> relationDeletes
    ) {
        return new EditSetRequest(
                executionId, ownerId, null, UUID.randomUUID(), UUID.randomUUID(),
                "hash", mapper.createObjectNode(), mapper.createObjectNode(), idempotencyKey,
                EditSetRequest.Expectations.empty(),
                creates, updates, deletes, relationCreates, relationDeletes
        );
    }

    protected EditSetRequest.ObjectUpdate editSetUpdate(
            UUID objectTypeId,
            String primaryKey,
            java.util.Map<UUID, JsonNode> set,
            List<UUID> clear
    ) {
        return new EditSetRequest.ObjectUpdate(objectTypeId, primaryKey, set, clear);
    }

    protected JsonNode textValue(String value) {
        return mapper.getNodeFactory().textNode(value);
    }

    protected long countRows(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    protected ObjectFixture customerFixture() {
        return createObjectFixture(UUID.randomUUID(), List.of(
                new AttributeSpec(UUID.randomUUID(), "id", "text"),
                new AttributeSpec(UUID.randomUUID(), "name", "text"),
                new AttributeSpec(UUID.randomUUID(), "level", "text")
        ));
    }
}
