package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;

import java.util.UUID;

public interface PipelinePreviewService {

    PreviewResponse preview(UUID pipelineId, UUID ownerId, PreviewRequest request);

    default void cancelForBuild(UUID pipelineId) {
    }
}
