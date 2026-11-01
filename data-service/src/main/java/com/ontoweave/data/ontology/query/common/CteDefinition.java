package com.ontoweave.data.ontology.query.common;

import java.util.List;

public record CteDefinition(String name, String body, List<Object> arguments) {

    public CteDefinition {
        if (name == null || !name.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("invalid CTE name");
        }
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public static CteDefinition of(String name, SqlFragment fragment) {
        return new CteDefinition(name, fragment.sql(), fragment.arguments());
    }
}
