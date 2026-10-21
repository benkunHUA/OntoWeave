package com.ontoweave.data.pipeline.execution.flink;

public class FlinkPreviewTimeoutException extends RuntimeException {

    public FlinkPreviewTimeoutException(String message) {
        super(message);
    }
}
