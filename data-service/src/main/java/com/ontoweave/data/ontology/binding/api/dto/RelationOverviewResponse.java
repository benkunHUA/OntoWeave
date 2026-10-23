package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.mapping.MappingValidationResult;

import java.util.List;

public record RelationOverviewResponse(
        RelationDefinitionResponse relation,
        ObjectBindingSummary source,
        ObjectBindingSummary target,
        List<MappingValidationResult.MappingIssue> warnings,
        RelationVersionSummary latestVersion,
        IndexRunResponse latestRun
) {

    public RelationOverviewResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
