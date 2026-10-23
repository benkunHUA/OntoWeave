package com.ontoweave.data.ontology.common;

public final class OntologyErrorCode {

    public static final String MAPPING_REVISION_CONFLICT = "MAPPING_REVISION_CONFLICT";
    public static final String MAPPING_INVALID = "MAPPING_INVALID";
    public static final String DATASET_NOT_FOUND = "DATASET_NOT_FOUND";
    public static final String DATASET_NOT_BOUND = "DATASET_NOT_BOUND";
    public static final String BINDING_CONFLICT = "BINDING_CONFLICT";
    public static final String RUN_NOT_FOUND = "RUN_NOT_FOUND";
    public static final String RUN_NOT_CANCELLABLE = "RUN_NOT_CANCELLABLE";
    public static final String WORKER_LOST = "WORKER_LOST";

    private OntologyErrorCode() {
    }
}
