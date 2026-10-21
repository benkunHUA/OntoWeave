package com.ontoweave.data.pipeline.execution.plan;

import java.util.List;

public record PlanStep(
        String nodeId,
        String nodeType,
        LogicalPlanNode node,
        List<PlanStep> inputs
) {

    public PlanStep {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
