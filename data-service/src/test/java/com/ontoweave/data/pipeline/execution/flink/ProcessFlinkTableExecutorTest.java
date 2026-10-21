package com.ontoweave.data.pipeline.execution.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.execution.plan.CompiledTablePlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import com.ontoweave.data.pipeline.execution.plan.PlanStep;
import com.ontoweave.data.pipeline.execution.plan.ScanLimitAssessment;
import com.ontoweave.data.pipeline.execution.plan.ScanLimitEvaluator;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.DataType;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessFlinkTableExecutorTest {

    private final PipelinePlanCompiler compiler = mock(PipelinePlanCompiler.class);
    private final ScanLimitEvaluator scanLimits = mock(ScanLimitEvaluator.class);
    private final ProcessFlinkTableExecutor executor = new ProcessFlinkTableExecutor(compiler, scanLimits);

    @Test
    void preservesCompileFailureMappedToItsPlanStep() {
        LogicalPlan plan = transformPlan();
        PipelineExecutionException failure = PipelineExecutionException.at(
                plan.root(), new IllegalArgumentException("bad expression")
        );
        when(compiler.compile(any())).thenThrow(failure);

        assertThatThrownBy(() -> executor.execute(plan, 500, Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(PipelineExecutionException.class, exception -> {
                    assertThat(exception.nodeId()).isEqualTo(plan.targetNodeId());
                    assertThat(exception.portId()).isEqualTo("in");
                    assertThat(exception.field()).isNull();
                });
    }

    @Test
    void omitsLocationWhenExecutionFailureCannotBeMappedToAStep() {
        LogicalPlan plan = transformPlan();
        when(compiler.compile(any())).thenThrow(new IllegalArgumentException("bad expression"));

        assertThatThrownBy(() -> executor.execute(plan, 500, Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(PipelineExecutionException.class, exception -> {
                    assertThat(exception.nodeId()).isNull();
                    assertThat(exception.portId()).isNull();
                    assertThat(exception.field()).isNull();
                });
    }

    @Test
    void mapsHiddenCastFlagToLocatedPreviewIssueWithoutInventingScannedRows() throws Exception {
        LogicalPlan plan = transformPlan();
        CompiledTablePlan.HiddenErrorColumn hidden = new CompiledTablePlan.HiddenErrorColumn(
                plan.targetNodeId(), "in", "parsed",
                "__ontoweave_preview_0_flag", "__ontoweave_preview_0_original", true
        );
        CompiledTablePlan compiled = compiled(hidden);
        when(compiler.compile(any())).thenReturn(compiled);
        when(scanLimits.evaluate(plan)).thenReturn(new ScanLimitAssessment(true, true));

        FlinkExecutionResult result = executor.execute(plan, 500, Duration.ofSeconds(1));

        assertThat(result.scannedRows()).isNull();
        assertThat(result.scanLimited()).isTrue();
        assertThat(result.columns()).extracting(column -> column.name()).containsExactly("parsed");
        assertThat(result.rows()).singleElement()
                .satisfies(row -> assertThat(row).containsOnlyKeys("parsed"));
        assertThat(result.errors()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("TYPE_MISMATCH");
            assertThat(issue.phase()).isEqualTo("execution");
            assertThat(issue.level()).isEqualTo("warning");
            assertThat(issue.nodeId()).isEqualTo(plan.targetNodeId());
            assertThat(issue.portId()).isEqualTo("in");
            assertThat(issue.field()).isEqualTo("parsed");
            assertThat(issue.rowNumber()).isEqualTo(1L);
            assertThat(issue.originalValue()).isEqualTo("abc");
        });
    }


    @Test
    void warnsWhenScanMetadataIsUnavailableInsteadOfReportingComplete() throws Exception {
        LogicalPlan plan = transformPlan();
        CompiledTablePlan.HiddenErrorColumn hidden = new CompiledTablePlan.HiddenErrorColumn(
                plan.targetNodeId(), "in", "parsed",
                "__ontoweave_preview_0_flag", "__ontoweave_preview_0_original", true
        );
        CompiledTablePlan compiled = compiled(hidden);
        when(compiler.compile(any())).thenReturn(compiled);
        when(scanLimits.evaluate(plan)).thenReturn(new ScanLimitAssessment(false, false));

        FlinkExecutionResult result = executor.execute(plan, 500, Duration.ofSeconds(1));

        assertThat(result.scannedRows()).isNull();
        assertThat(result.scanLimited()).isFalse();
        assertThat(result.warnings()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("SCAN_ROW_LIMIT_UNKNOWN");
            assertThat(issue.phase()).isEqualTo("scan");
            assertThat(issue.level()).isEqualTo("warning");
            assertThat(issue.nodeId()).isNull();
        });
    }

    @Test
    void leavesRowNumberUnknownWhenAggregationMakesRowsImprecise() throws Exception {
        LogicalPlan plan = transformPlan();
        CompiledTablePlan.HiddenErrorColumn hidden = new CompiledTablePlan.HiddenErrorColumn(
                plan.targetNodeId(), "in", "parsed",
                "__ontoweave_preview_0_flag", "__ontoweave_preview_0_original", false
        );
        CompiledTablePlan compiled = compiled(hidden);
        when(compiler.compile(any())).thenReturn(compiled);
        when(scanLimits.evaluate(plan)).thenReturn(new ScanLimitAssessment(false, true));

        FlinkExecutionResult result = executor.execute(plan, 500, Duration.ofSeconds(1));

        assertThat(result.errors()).singleElement()
                .extracting(issue -> issue.rowNumber())
                .isNull();
    }

    private CompiledTablePlan compiled(CompiledTablePlan.HiddenErrorColumn hidden) {
        Table table = mock(Table.class);
        when(table.limit(anyInt(), anyInt())).thenReturn(table);
        TableResult result = mock(TableResult.class);
        when(table.execute()).thenReturn(result);
        when(result.getJobClient()).thenReturn(Optional.empty());
        when(result.getResolvedSchema()).thenReturn(ResolvedSchema.physical(
                new String[]{"parsed", hidden.flagColumn(), hidden.originalColumn()},
                new DataType[]{DataTypes.STRING(), DataTypes.BOOLEAN(), DataTypes.STRING()}
        ));
        when(result.collect()).thenReturn(CloseableIterator.adapterForIterator(
                List.of(Row.of(null, true, "abc")).iterator()
        ));
        return new CompiledTablePlan(mock(org.apache.flink.table.api.bridge.java.StreamTableEnvironment.class),
                table, List.of(hidden));
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
