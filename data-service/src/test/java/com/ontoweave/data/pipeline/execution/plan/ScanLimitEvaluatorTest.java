package com.ontoweave.data.pipeline.execution.plan;

import com.ontoweave.data.sync.iceberg.IcebergSnapshotMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScanLimitEvaluatorTest {

    private final IcebergSnapshotMetrics metrics = mock(IcebergSnapshotMetrics.class);
    private final ScanLimitEvaluator evaluator = new ScanLimitEvaluator(metrics);

    @Test
    void marksScanLimitWhenInputRecordCountExceedsFiftyThousand() {
        LogicalPlan plan = plan(50_000);
        when(metrics.recordCount("ontoweave", "sales", "orders", 100L))
                .thenReturn(Optional.of(50_001L));

        ScanLimitAssessment assessment = evaluator.evaluate(plan);

        assertThat(assessment.scanLimited()).isTrue();
        assertThat(assessment.scannedRows()).isEqualTo(50_001L);
    }

    @Test
    void doesNotMarkScanLimitAtExactlyFiftyThousand() {
        LogicalPlan plan = plan(50_000);
        when(metrics.recordCount("ontoweave", "sales", "orders", 100L))
                .thenReturn(Optional.of(50_000L));

        ScanLimitAssessment assessment = evaluator.evaluate(plan);

        assertThat(assessment.scanLimited()).isFalse();
        assertThat(assessment.scannedRows()).isEqualTo(50_000L);
    }

    @Test
    void leavesScannedRowsUnknownWhenMetadataIsUnavailable() {
        LogicalPlan plan = plan(50_000);
        when(metrics.recordCount("ontoweave", "sales", "orders", 100L))
                .thenReturn(Optional.empty());

        ScanLimitAssessment assessment = evaluator.evaluate(plan);

        assertThat(assessment.scanLimited()).isFalse();
        assertThat(assessment.scannedRows()).isNull();
    }

    private LogicalPlan plan(int scanLimit) {
        PlanStep root = new PlanStep(
                UUID.randomUUID().toString(),
                "input",
                new LogicalPlanNode.ScanInput("ontoweave", "sales", "orders", 100L, scanLimit),
                List.of()
        );
        return LogicalPlan.of(root, 4L, root.nodeId(), Map.of());
    }
}
