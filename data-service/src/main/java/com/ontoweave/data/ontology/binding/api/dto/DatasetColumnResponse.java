package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.dataset.DatasetColumn;

public record DatasetColumnResponse(
        String name,
        String type,
        boolean optional,
        Object example
) {

    public static DatasetColumnResponse from(DatasetColumn column) {
        return new DatasetColumnResponse(
                column.name(),
                column.type(),
                column.optional(),
                column.example()
        );
    }
}
