package com.ontoweave.data.pipeline.execution.build;

import java.util.UUID;

public interface PipelineVersionFreezer {

    FrozenBuild freeze(UUID pipelineId, UUID ownerId, long draftRevision);
}
