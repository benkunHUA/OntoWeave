package com.ontoweave.data.ontology.query.repository;

import java.util.List;

public record RelatedPage(List<RelatedRow> items, long total) {

    public RelatedPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
