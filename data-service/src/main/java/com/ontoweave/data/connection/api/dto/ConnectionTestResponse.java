package com.ontoweave.data.connection.api.dto;

import java.time.Instant;
import java.util.Map;

public record ConnectionTestResponse(
        String status,
        long latencyMs,
        String serverVersion,
        String errorCode,
        String message,
        Instant testedAt,
        Map<String, Object> details
) {
}
