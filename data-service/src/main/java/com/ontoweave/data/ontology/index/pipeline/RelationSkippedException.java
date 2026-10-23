package com.ontoweave.data.ontology.index.pipeline;

public class RelationSkippedException extends RuntimeException {

    private final String reason;

    public RelationSkippedException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
