package com.ontoweave.data.ontology.query.repository;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record RelationStepRows(List<PeerRow> items, long total) {

    public RelationStepRows {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record PeerRow(String primaryKey, String title, JsonNode properties) {
    }
}
