package com.ontoweave.data.pipeline.execution.build;

public class PipelineBuildException extends RuntimeException {

    private final String errorCode;

    public PipelineBuildException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PipelineBuildException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
