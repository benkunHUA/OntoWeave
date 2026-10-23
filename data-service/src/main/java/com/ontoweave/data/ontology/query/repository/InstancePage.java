package com.ontoweave.data.ontology.query.repository;

import java.util.List;

public record InstancePage(List<InstanceRow> items, long total) {

    public InstancePage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
