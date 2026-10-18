package com.ontoweave.data.sync.flink;

public class FlinkSubmitException extends RuntimeException {

    private final String errorCode;

    public FlinkSubmitException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
