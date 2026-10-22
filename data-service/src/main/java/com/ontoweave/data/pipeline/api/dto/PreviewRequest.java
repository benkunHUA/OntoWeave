package com.ontoweave.data.pipeline.api.dto;

import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PreviewRequest(
        long draftRevision,
        @NotNull String nodeId,
        @NotNull UUID requestId,
        @Min(1) @Max(PipelineExecutionLimits.MAX_PREVIEW_ROW_LIMIT) Integer rowLimit
) {

    public PreviewRequest(long draftRevision, String nodeId, UUID requestId) {
        this(
                draftRevision,
                nodeId,
                requestId,
                PipelineExecutionLimits.DEFAULT_PREVIEW_ROW_LIMIT
        );
    }

    public PreviewRequest {
        if (rowLimit == null) {
            rowLimit = PipelineExecutionLimits.DEFAULT_PREVIEW_ROW_LIMIT;
        }
    }
}
