package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;

public record RelationGroupsResponse(List<RelationGroupResponse> groups) {

    public RelationGroupsResponse {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
