package com.ontoweave.data.pipeline.execution.build;

public record ExpectationResult(
        String type,
        boolean passed,
        Object actual,
        Object expected,
        long failedRows,
        String message
) {
}
