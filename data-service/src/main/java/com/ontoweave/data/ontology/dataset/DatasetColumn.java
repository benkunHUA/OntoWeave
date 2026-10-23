package com.ontoweave.data.ontology.dataset;

public record DatasetColumn(
        String name,
        String type,
        boolean optional,
        Object example
) {
}
