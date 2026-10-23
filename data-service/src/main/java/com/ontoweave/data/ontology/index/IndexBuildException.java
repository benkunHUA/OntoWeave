package com.ontoweave.data.ontology.index;

import java.util.Map;

public class IndexBuildException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> details;

    public IndexBuildException(String errorCode, String message) {
        this(errorCode, message, Map.of(), null);
    }

    public IndexBuildException(
            String errorCode,
            String message,
            Map<String, Object> details
    ) {
        this(errorCode, message, details, null);
    }

    public IndexBuildException(
            String errorCode,
            String message,
            Map<String, Object> details,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
