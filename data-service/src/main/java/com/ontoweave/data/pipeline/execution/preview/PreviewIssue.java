package com.ontoweave.data.pipeline.execution.preview;

public record PreviewIssue(
        String code,
        String phase,
        String level,
        String nodeId,
        String portId,
        String field,
        Long rowNumber,
        String message,
        String hint,
        String originalValue
) {
}
