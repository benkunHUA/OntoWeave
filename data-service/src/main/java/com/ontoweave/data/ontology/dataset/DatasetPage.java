package com.ontoweave.data.ontology.dataset;

import java.util.List;

public record DatasetPage(
        List<DatasetRef> items,
        long total,
        int page,
        int size
) {

    public DatasetPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
