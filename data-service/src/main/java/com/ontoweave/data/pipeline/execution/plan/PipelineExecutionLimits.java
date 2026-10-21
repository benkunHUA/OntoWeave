package com.ontoweave.data.pipeline.execution.plan;

import java.time.Duration;

public final class PipelineExecutionLimits {

    public static final int DEFAULT_SCAN_LIMIT = 50_000;
    public static final int OUTPUT_ROW_LIMIT = 500;
    public static final int READ_ROW_LIMIT = OUTPUT_ROW_LIMIT + 1;
    public static final Duration PREVIEW_TIMEOUT = Duration.ofSeconds(20);

    private PipelineExecutionLimits() {
    }
}
