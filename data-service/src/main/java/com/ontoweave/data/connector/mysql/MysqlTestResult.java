package com.ontoweave.data.connector.mysql;

import java.util.Map;

public record MysqlTestResult(
        boolean success,
        long latencyMs,
        String serverVersion,
        String errorCode,
        String message,
        Map<String, Object> details
) {
}
