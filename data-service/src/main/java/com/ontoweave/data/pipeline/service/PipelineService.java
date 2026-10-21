package com.ontoweave.data.pipeline.service;

import com.ontoweave.data.pipeline.api.dto.CopyPipelineRequest;
import com.ontoweave.data.pipeline.api.dto.PipelinePageResponse;

import java.util.UUID;

public interface PipelineService {

    PipelineCreateResult create(CreatePipelineRequest request, UUID operationId);

    PipelinePageResponse list(UUID ownerId, int limit, int offset);

    PipelineResponse get(UUID pipelineId, UUID ownerId);

    PipelineResponse rename(UUID pipelineId, UUID ownerId, String name);

    void archive(UUID pipelineId, UUID ownerId);

    PipelineResponse copy(UUID pipelineId, UUID ownerId, CopyPipelineRequest request);
}
