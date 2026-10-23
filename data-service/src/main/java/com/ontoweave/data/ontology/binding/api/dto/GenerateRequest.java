package com.ontoweave.data.ontology.binding.api.dto;

import java.util.UUID;

public record GenerateRequest(
        UUID ownerId,
        long mappingRevision
) {
}
