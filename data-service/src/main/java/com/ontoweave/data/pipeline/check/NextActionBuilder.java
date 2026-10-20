package com.ontoweave.data.pipeline.check;

import com.ontoweave.data.pipeline.graph.PipelineDraftGraph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class NextActionBuilder {

    List<NextAction> build(
            PipelineStage stage,
            PipelineDraftGraph graph,
            List<PipelineIssue> issues
    ) {
        Set<NextAction> actions = new LinkedHashSet<>();
        if (stage == PipelineStage.NEEDS_INPUT) {
            actions.add(new NextAction("add_input", null));
        }
        if (stage == PipelineStage.NEEDS_OUTPUT) {
            actions.add(new NextAction("add_output", null));
        }
        for (PipelineIssue issue : issues) {
            switch (issue.code()) {
                case PipelineIssueCodes.NODE_CONFIG_INCOMPLETE ->
                        actions.add(new NextAction("configure_node", issue.nodeId()));
                case PipelineIssueCodes.INPUT_MISSING ->
                        actions.add(new NextAction("connect_input", issue.nodeId()));
                case PipelineIssueCodes.OUTPUT_NOT_CONNECTED ->
                        actions.add(new NextAction("connect_output", issue.nodeId()));
                case PipelineIssueCodes.OUTPUT_MISSING -> actions.add(new NextAction(
                        issue.nodeId() == null ? "add_output" : "set_output",
                        issue.nodeId()
                ));
                default -> {
                }
            }
        }
        if (graph.nodes().isEmpty()) {
            actions.add(new NextAction("add_input", null));
        }
        return new ArrayList<>(actions);
    }
}
