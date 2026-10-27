package com.ontoweave.data.ontology.query.common;

import com.ontoweave.data.ontology.query.filter.QueryDatatype;

import java.util.UUID;

public record SortSpec(UUID attributeId, QueryDatatype datatype, boolean descending) {
}
