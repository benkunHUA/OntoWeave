package com.ontoweave.data.pipeline.execution.plan;

import com.ontoweave.data.pipeline.execution.build.ResolvedInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record LogicalPlan(
        PlanStep root,
        long draftRevision,
        String targetNodeId,
        Map<String, ResolvedInput> inputsByNodeId
) {

    public LogicalPlan {
        inputsByNodeId = inputsByNodeId == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(inputsByNodeId));
    }

    public static LogicalPlan of(
            PlanStep root,
            long draftRevision,
            String targetNodeId,
            Map<String, ResolvedInput> inputsByNodeId
    ) {
        return new LogicalPlan(root, draftRevision, targetNodeId, inputsByNodeId);
    }

    public List<PlanStep> nodes() {
        Map<String, PlanStep> nodes = new LinkedHashMap<>();
        collect(root, nodes);
        return new ArrayList<>(nodes.values());
    }

    public List<ResolvedInput> inputVersions() {
        Set<UUID> seen = new LinkedHashSet<>();
        List<ResolvedInput> inputs = new ArrayList<>();
        for (ResolvedInput input : inputsByNodeId.values()) {
            if (seen.add(input.datasetVersionId())) {
                inputs.add(input);
            }
        }
        return inputs;
    }

    private void collect(PlanStep step, Map<String, PlanStep> nodes) {
        if (step == null || nodes.putIfAbsent(step.nodeId(), step) != null) {
            return;
        }
        step.inputs().forEach(input -> collect(input, nodes));
    }
}
