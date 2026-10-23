package com.ontoweave.data.ontology.index;

public final class IndexFailureCode {

    public static final String MAPPING_INVALID = "MAPPING_INVALID";
    public static final String DATASET_NOT_FOUND = "DATASET_NOT_FOUND";
    public static final String DATASET_VERSION_MISMATCH = "DATASET_VERSION_MISMATCH";
    public static final String TYPE_CONVERSION_FAILED = "TYPE_CONVERSION_FAILED";
    public static final String ENUM_OUT_OF_RANGE = "ENUM_OUT_OF_RANGE";
    public static final String STAGE_VALIDATION_FAILED = "STAGE_VALIDATION_FAILED";
    public static final String RELATION_ENDPOINT_MISSING = "RELATION_ENDPOINT_MISSING";
    public static final String RUN_NOT_RETRYABLE = "RUN_NOT_RETRYABLE";
    public static final String LEASE_LOST = "LEASE_LOST";
    public static final String SUPERSEDED = "SUPERSEDED";
    public static final String INDEX_FAILURE = "INDEX_FAILURE";

    private IndexFailureCode() {
    }
}
