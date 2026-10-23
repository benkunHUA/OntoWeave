package com.ontoweave.data.ontology.query.repository;

import com.ontoweave.data.ontology.query.filter.QueryDatatype;

import java.util.UUID;

public record RelationDefinitionRow(
        UUID relationId,
        UUID sourceTypeId,
        UUID targetTypeId,
        String name,
        String sourcePrimaryKeyDatatype,
        String targetPrimaryKeyDatatype
) {

    public QueryDatatype sourcePrimaryKeyType() {
        return QueryDatatype.from(sourcePrimaryKeyDatatype);
    }

    public QueryDatatype targetPrimaryKeyType() {
        return QueryDatatype.from(targetPrimaryKeyDatatype);
    }
}
