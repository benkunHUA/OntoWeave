package com.ontoweave.data.pipeline.execution.plan;

import java.time.Duration;

public final class PipelineExecutionLimits {

    public static final int DEFAULT_SCAN_LIMIT = 50_000;
    public static final int OUTPUT_ROW_LIMIT = 500;
    public static final int READ_ROW_LIMIT = OUTPUT_ROW_LIMIT + 1;
    public static final int MAX_ACTIVE_PREVIEWS_PER_PIPELINE = 1;
    public static final int MAX_ACTIVE_PREVIEWS_PER_USER = 2;
    public static final int MAX_PREVIEW_WORKERS = 8;
    public static final long PREVIEW_WORKER_KEEP_ALIVE_SECONDS = 60;
    public static final Duration PREVIEW_TIMEOUT = Duration.ofSeconds(20);
    public static final Duration PREVIEW_RELEASE_GRACE = Duration.ofSeconds(5);
    public static final Duration PREVIEW_EPOCH_STATE_TTL = Duration.ofMinutes(10);

    private PipelineExecutionLimits() {
    }
}
