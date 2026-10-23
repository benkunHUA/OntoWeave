package com.ontoweave.data.ontology.query.common;

import com.ontoweave.data.common.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public final class QueryErrors {

    private QueryErrors() {
    }

    public static ApiException invalidRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, QueryConstants.VALIDATION_ERROR, message);
    }

    public static ApiException invalidMapping(String message, Map<String, Object> details) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                QueryConstants.MAPPING_INVALID,
                message,
                details
        );
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, QueryConstants.NOT_FOUND, message);
    }
}
