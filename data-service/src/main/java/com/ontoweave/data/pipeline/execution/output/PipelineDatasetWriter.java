package com.ontoweave.data.pipeline.execution.output;

import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;

import java.util.UUID;

public interface PipelineDatasetWriter {

    WrittenSnapshot write(UUID buildId, UUID outputId, LogicalPlan plan, String branch);

    void fastForwardMain(UUID datasetId, long snapshotId);

    void dropBranch(UUID datasetId, String branch);

    default void cancel(UUID buildId) {
    }
}
