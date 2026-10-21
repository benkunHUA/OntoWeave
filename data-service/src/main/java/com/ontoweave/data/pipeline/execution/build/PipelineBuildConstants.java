package com.ontoweave.data.pipeline.execution.build;

public final class PipelineBuildConstants {

    public static final String EVENT_SUCCEEDED = "pipeline.build.succeeded";
    public static final String AGGREGATE_PIPELINE = "pipeline";
    public static final String BRANCH_PREFIX = "build_";
    public static final String CODE_NOT_READY = "PIPELINE_NOT_READY";
    public static final String CODE_ACTIVE = "BUILD_ACTIVE";
    public static final String CODE_WORKER_LOST = "BUILD_WORKER_LOST";
    public static final String CODE_PUBLISH_FAILED = "PUBLISH_FAILED";
    public static final String CODE_EXECUTION_FAILED = "BUILD_EXECUTION_FAILED";
    public static final String CODE_OUTPUT_SCHEMA_CHANGED = "OUTPUT_SCHEMA_CHANGED";
    public static final String CODE_PREVIEW_BUSY = "PREVIEW_BUSY";
    public static final String CODE_CANCELED = "BUILD_CANCELED";
    public static final String CODE_EXPECTATION_FAILED = "EXPECTATION_FAILED";
    public static final String CODE_NOT_FOUND = "NOT_FOUND";

    private PipelineBuildConstants() {
    }
}
