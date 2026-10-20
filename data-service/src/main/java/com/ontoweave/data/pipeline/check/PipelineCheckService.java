package com.ontoweave.data.pipeline.check;

import java.util.UUID;

public interface PipelineCheckService {

    PipelineCheckResult check(UUID pipelineId, UUID ownerId, long revision);
}
