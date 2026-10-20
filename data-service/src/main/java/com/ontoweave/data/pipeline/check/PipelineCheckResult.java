package com.ontoweave.data.pipeline.check;

import java.util.List;
import java.util.UUID;

public record PipelineCheckResult(
        UUID pipelineId,
        long revision,
        PipelineStage stage,
        List<PipelineIssue> blockingIssues,
        List<PipelineIssue> warnings,
        List<NextAction> nextActions
) {

    public PipelineCheckResult {
        blockingIssues = blockingIssues == null ? List.of() : List.copyOf(blockingIssues);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
    }
}
