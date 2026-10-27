package com.ontoweave.data.ontology.query.api.dto;

import java.util.UUID;

public record ObjectReference(UUID objectTypeId, String primaryKey) {
}
