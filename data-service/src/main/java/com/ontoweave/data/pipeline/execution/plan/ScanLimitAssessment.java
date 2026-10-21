package com.ontoweave.data.pipeline.execution.plan;

public record ScanLimitAssessment(boolean scanLimited, boolean metadataAvailable) {

    public static ScanLimitAssessment none() {
        return new ScanLimitAssessment(false, false);
    }
}
