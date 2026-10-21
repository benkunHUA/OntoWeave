package com.ontoweave.data.pipeline.check;

import java.util.UUID;

public interface PipelineCheckService {

    void validateRevision(UUID pipelineId, UUID ownerId, long revision);

    PipelineCheckResult check(UUID pipelineId, UUID ownerId, long revision);
}
