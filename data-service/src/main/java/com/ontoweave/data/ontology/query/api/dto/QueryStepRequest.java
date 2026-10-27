package com.ontoweave.data.ontology.query.api.dto;

import com.ontoweave.data.ontology.query.filter.FilterCondition;

import java.util.List;
import java.util.UUID;

public record QueryStepRequest(
        UUID ownerId,
        UUID relationId,
        String direction,
        List<ObjectReference> inputs,
        List<FilterCondition> filters,
        List<SortCondition> sort,
        Integer limit,
        List<UUID> project
) {

    public QueryStepRequest {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        filters = filters == null ? List.of() : List.copyOf(filters);
        sort = sort == null ? List.of() : List.copyOf(sort);
        project = project == null ? List.of() : List.copyOf(project);
    }
}
