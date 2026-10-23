package com.ontoweave.data.ontology.query.filter;

import java.util.List;

public record ParsedFilters(List<String> clauses, List<Object> parameters) {

    public ParsedFilters {
        clauses = clauses == null ? List.of() : List.copyOf(clauses);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public static ParsedFilters empty() {
        return new ParsedFilters(List.of(), List.of());
    }

    public boolean isEmpty() {
        return clauses.isEmpty();
    }
}
