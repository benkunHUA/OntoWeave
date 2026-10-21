package com.ontoweave.data.pipeline.execution.plan;

public record ScanLimitAssessment(boolean scanLimited, Long scannedRows) {

    public static ScanLimitAssessment none() {
        return new ScanLimitAssessment(false, null);
    }
}
