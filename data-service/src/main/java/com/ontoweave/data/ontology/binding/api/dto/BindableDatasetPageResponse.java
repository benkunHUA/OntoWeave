package com.ontoweave.data.ontology.binding.api.dto;

import java.util.List;

public record BindableDatasetPageResponse(
        List<BindableDatasetResponse> items,
        long total,
        int page,
        int size
) {

    public BindableDatasetPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
