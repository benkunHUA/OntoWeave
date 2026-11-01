package com.ontoweave.data.ontology.edit.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

record EditSetAuditChange(
        String kind,
        UUID objectTypeId,
        String primaryKey,
        UUID attributeId,
        UUID relationId,
        UUID fromTypeId,
        String fromPrimaryKey,
        UUID toTypeId,
        String toPrimaryKey,
        JsonNode before,
        JsonNode after
) {
}
