package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;

import java.util.List;
import java.util.UUID;

public interface PipelineInputResolver {

    List<ResolvedInput> resolve(UUID pipelineId, UUID ownerId, long draftRevision);

    List<ResolvedInput> resolve(PipelineVersionEntity version);
}
