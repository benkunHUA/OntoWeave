package com.ontoweave.data.ontology.query.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SortCondition(
        @JsonProperty("attributeId") String attributeId,
        @JsonProperty("direction") String direction
) {
}
