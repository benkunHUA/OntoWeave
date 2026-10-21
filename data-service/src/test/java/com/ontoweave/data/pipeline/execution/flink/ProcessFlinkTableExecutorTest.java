package com.ontoweave.data.pipeline.execution.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import com.ontoweave.data.pipeline.execution.plan.PlanStep;
import com.ontoweave.data.pipeline.execution.plan.ScanLimitEvaluator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessFlinkTableExecutorTest {

    private final PipelinePlanCompiler compiler = mock(PipelinePlanCompiler.class);
    private final ScanLimitEvaluator scanLimits = mock(ScanLimitEvaluator.class);
    private final ProcessFlinkTableExecutor executor = new ProcessFlinkTableExecutor(compiler, scanLimits);

    @Test
    void mapsExecutionExceptionToPlanNodeAndInputPort() {
        LogicalPlan plan = transformPlan();
        when(compiler.compile(any())).thenThrow(new IllegalArgumentException("bad expression"));

        assertThatThrownBy(() -> executor.execute(plan, 500, Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(PipelineExecutionException.class, exception -> {
                    assertThat(exception.nodeId()).isEqualTo(plan.targetNodeId());
                    assertThat(exception.portId()).isEqualTo("in");
                    assertThat(exception.field()).isNull();
                });
    }

    private LogicalPlan transformPlan() {
        PlanStep scan = new PlanStep(
                UUID.randomUUID().toString(),
                "input",
                new LogicalPlanNode.ScanInput("ontoweave", "sales", "orders", 100L, 50_000),
                List.of()
        );
        PlanStep filter = new PlanStep(
                UUID.randomUUID().toString(),
                "filter",
                new LogicalPlanNode.Filter(new ObjectMapper().createObjectNode()),
                List.of(scan)
        );
        return LogicalPlan.of(filter, 4L, filter.nodeId(), Map.of());
    }
}
