package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;

import java.time.Duration;
import java.util.UUID;

public interface PipelinePreviewService {

    PreviewResponse preview(UUID pipelineId, UUID ownerId, PreviewRequest request);

    void cancelForBuild(UUID pipelineId);

    boolean awaitReleased(UUID pipelineId, Duration timeout);
}
