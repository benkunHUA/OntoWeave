package com.ontoweave.data.ontology.query.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record OntologyPropertyResponse(UUID attributeId, JsonNode value) {
}
