package com.ontoweave.data.pipeline.execution.plan;

/**
 * Scan-limit facts gathered from Iceberg metadata.
 *
 * <p>{@code knownInputRows} is the sum of DataFile record counts when every input
 * snapshot is readable. It is not an execution-level scanned-row metric and must
 * never be copied into {@code FlinkExecutionResult.scannedRows}.</p>
 */
public record ScanLimitAssessment(
        boolean scanLimited,
        boolean metadataAvailable,
        Long knownInputRows
) {

    public ScanLimitAssessment(boolean scanLimited, boolean metadataAvailable) {
        this(scanLimited, metadataAvailable, null);
    }

    public static ScanLimitAssessment none() {
        return new ScanLimitAssessment(false, false, null);
    }
}
