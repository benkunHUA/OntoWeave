package com.ontoweave.data.pipeline.execution.build;

import java.util.UUID;

public interface PipelineBuildService {

    BuildResponse enqueue(UUID pipelineId, UUID ownerId, CreateBuildRequest request);

    UUID enqueueScheduled(EnqueueScheduledBuild command);

    BuildResponse get(UUID pipelineId, UUID buildId, UUID ownerId);

    void requestCancel(UUID pipelineId, UUID buildId, UUID ownerId);
}
