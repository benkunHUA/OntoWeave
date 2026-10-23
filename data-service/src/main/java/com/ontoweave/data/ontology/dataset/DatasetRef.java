package com.ontoweave.data.ontology.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;

import java.util.Objects;
import java.util.UUID;

public record DatasetRef(
        DatasetKind kind,
        UUID datasetId,
        UUID versionId,
        String catalog,
        String namespace,
        String table,
        long snapshotId,
        JsonNode schemaJson,
        UUID ownerId,
        long rowCount
) {

    public DatasetRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
