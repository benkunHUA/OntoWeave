package com.ontoweave.data.pipeline.execution.plan;

import com.ontoweave.data.sync.iceberg.IcebergSnapshotMetrics;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class ScanLimitEvaluator {

    private final IcebergSnapshotMetrics metrics;

    public ScanLimitEvaluator(IcebergSnapshotMetrics metrics) {
        this.metrics = metrics;
    }

    public ScanLimitAssessment evaluate(LogicalPlan plan) {
        Map<String, LogicalPlanNode.ScanInput> scans = new LinkedHashMap<>();
        for (PlanStep step : plan.nodes()) {
            if (step.node() instanceof LogicalPlanNode.ScanInput scan) {
                scans.merge(scanKey(scan), scan, (left, right) -> stricter(left, right));
            }
        }

        boolean known = true;
        boolean limited = false;
        long total = 0L;
        for (LogicalPlanNode.ScanInput scan : scans.values()) {
            Optional<Long> count = metrics.recordCount(
                    scan.catalog(), scan.namespace(), scan.table(), scan.snapshotId()
            );
            if (count.isEmpty()) {
                known = false;
                continue;
            }
            limited = limited || count.get() > scan.scanLimit();
            try {
                total = Math.addExact(total, count.get());
            } catch (ArithmeticException error) {
                known = false;
            }
        }
        return new ScanLimitAssessment(limited, known, known ? total : null);
    }

    private LogicalPlanNode.ScanInput stricter(
            LogicalPlanNode.ScanInput left,
            LogicalPlanNode.ScanInput right
    ) {
        return left.scanLimit() <= right.scanLimit() ? left : right;
    }

    private String scanKey(LogicalPlanNode.ScanInput scan) {
        return scan.catalog() + '\u0000' + scan.namespace() + '\u0000'
                + scan.table() + '\u0000' + scan.snapshotId();
    }
}
