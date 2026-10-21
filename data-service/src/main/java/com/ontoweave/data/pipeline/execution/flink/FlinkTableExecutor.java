package com.ontoweave.data.pipeline.execution.flink;

import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;

import java.time.Duration;
import java.util.UUID;

public interface FlinkTableExecutor {

    FlinkExecutionResult execute(LogicalPlan plan, int outputLimit, Duration timeout);

    void cancel(UUID executionId);
}
