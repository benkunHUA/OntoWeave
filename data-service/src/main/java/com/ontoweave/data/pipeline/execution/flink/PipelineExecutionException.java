package com.ontoweave.data.pipeline.execution.flink;

import com.ontoweave.data.pipeline.execution.plan.PlanStep;

public class PipelineExecutionException extends RuntimeException {

    private final String nodeId;
    private final String portId;
    private final String field;

    public PipelineExecutionException(
            String message,
            String nodeId,
            String portId,
            String field,
            Throwable cause
    ) {
        super(message, cause);
        this.nodeId = nodeId;
        this.portId = portId;
        this.field = field;
    }

    public static PipelineExecutionException at(PlanStep step, Throwable cause) {
        String message = cause == null || cause.getMessage() == null
                ? "Flink preview execution failed"
                : cause.getMessage();
        return new PipelineExecutionException(
                message, step.nodeId(), inputPort(step), null, cause
        );
    }

    public String nodeId() {
        return nodeId;
    }

    public String portId() {
        return portId;
    }

    public String field() {
        return field;
    }

    private static String inputPort(PlanStep step) {
        return switch (step.nodeType()) {
            case "join", "union", "input" -> null;
            default -> "in";
        };
    }
}
