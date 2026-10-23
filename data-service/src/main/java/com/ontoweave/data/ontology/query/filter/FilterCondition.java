package com.ontoweave.data.ontology.query.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record FilterCondition(
        @JsonProperty("attributeId") String attributeId,
        @JsonProperty("op") String op,
        @JsonProperty("value") JsonNode value
) {
}
