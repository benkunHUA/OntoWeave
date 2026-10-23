package com.ontoweave.data.ontology.dataset;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.OntologyErrorCode;

import java.util.UUID;

public class DatasetUnavailableException extends RuntimeException {

    private final DatasetKind kind;
    private final UUID datasetId;

    public DatasetUnavailableException(DatasetKind kind, UUID datasetId) {
        super("dataset is not visible, published or current: " + kind + "/" + datasetId);
        this.kind = kind;
        this.datasetId = datasetId;
    }

    public String code() {
        return OntologyErrorCode.DATASET_NOT_FOUND;
    }

    public DatasetKind kind() {
        return kind;
    }

    public UUID datasetId() {
        return datasetId;
    }
}
