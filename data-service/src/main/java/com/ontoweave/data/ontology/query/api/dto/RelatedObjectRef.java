package com.ontoweave.data.ontology.query.api.dto;

import java.util.UUID;

public record RelatedObjectRef(UUID objectTypeId, String primaryKey, String title) {
}
