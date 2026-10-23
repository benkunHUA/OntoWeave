package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;

import java.util.UUID;

public record MappingSaveResponse(
        UUID bindingId,
        long revision,
        MappingSnapshot mapping,
        MappingValidationResult validation
) {
}
