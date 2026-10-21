package com.ontoweave.data.pipeline.execution.build;

import java.util.List;
import java.util.UUID;

public interface PipelineInputResolver {

    List<ResolvedInput> resolve(UUID pipelineId, UUID ownerId, long draftRevision);
}
