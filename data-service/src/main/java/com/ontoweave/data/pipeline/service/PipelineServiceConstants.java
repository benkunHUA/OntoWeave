package com.ontoweave.data.pipeline.service;

final class PipelineServiceConstants {

    static final int MAX_NAME_LENGTH = 200;
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    static final String CODE_VALIDATION_ERROR = "VALIDATION_ERROR";
    static final String CODE_NOT_FOUND = "NOT_FOUND";
    static final String CODE_BINDING_CONFLICT = "PIPELINE_BINDING_CONFLICT";
    static final String CODE_OUTPUT_INCOMPLETE = "OUTPUT_CONFIGURATION_INCOMPLETE";

    static final String STATUS_DRAFT = "draft";
    static final String ACTOR_TYPE_USER = "user";
    static final String EMPTY_DRAFT = "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}";

    private PipelineServiceConstants() {
    }
}
