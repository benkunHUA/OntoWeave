package com.ontoweave.data.pipeline.check;

public record PipelineIssue(
        String code,
        String phase,
        boolean blocking,
        String nodeId,
        String portId,
        String field,
        String message,
        String hint
) {
}
