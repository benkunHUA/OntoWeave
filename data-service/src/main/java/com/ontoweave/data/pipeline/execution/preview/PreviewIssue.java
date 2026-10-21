package com.ontoweave.data.pipeline.execution.preview;

public record PreviewIssue(
        String code,
        String level,
        String nodeId,
        String field,
        Long rowNumber,
        String message,
        String originalValue
) {
}
