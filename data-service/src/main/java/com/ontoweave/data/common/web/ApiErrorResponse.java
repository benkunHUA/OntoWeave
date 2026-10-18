package com.ontoweave.data.common.web;

import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        String traceId
) {
}
