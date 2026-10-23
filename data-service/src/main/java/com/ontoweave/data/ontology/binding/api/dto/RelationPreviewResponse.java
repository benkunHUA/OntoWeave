package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.mapping.MappingValidationResult;

import java.util.List;

public record RelationPreviewResponse(
        List<RelationPreviewPair> sample,
        List<MappingValidationResult.MappingIssue> blocking,
        List<MappingValidationResult.MappingIssue> warnings,
        List<PreviewSkip> skipped
) {

    public RelationPreviewResponse {
        sample = sample == null ? List.of() : List.copyOf(sample);
        blocking = blocking == null ? List.of() : List.copyOf(blocking);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
}
