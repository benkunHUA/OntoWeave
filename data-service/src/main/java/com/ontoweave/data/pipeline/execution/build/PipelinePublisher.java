package com.ontoweave.data.pipeline.execution.build;

import java.util.UUID;

public interface PipelinePublisher {

    void publish(UUID buildId);
}
