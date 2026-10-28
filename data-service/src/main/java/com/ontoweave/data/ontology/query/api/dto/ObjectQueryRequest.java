package com.ontoweave.data.ontology.query.api.dto;

import com.ontoweave.data.ontology.query.filter.FilterCondition;

import java.util.List;
import java.util.UUID;

public record ObjectQueryRequest(
        UUID ownerId,
        String q,
        List<String> primaryKeys,
        List<FilterCondition> filters,
        List<SortCondition> sort,
        Integer limit,
        List<UUID> project
) {

    public ObjectQueryRequest {
        primaryKeys = primaryKeys == null ? List.of() : List.copyOf(primaryKeys);
        filters = filters == null ? List.of() : List.copyOf(filters);
        sort = sort == null ? List.of() : List.copyOf(sort);
        project = project == null ? List.of() : List.copyOf(project);
    }
}
