package com.ontoweave.data.ontology.acceptance;

import java.util.UUID;

record P3PerformanceResult(
        int batchSize,
        long rows,
        double totalMillis,
        double readMillis,
        double writeMillis,
        double listP95Millis,
        double filterP95Millis,
        long filterRows,
        long instanceRows,
        long valueRows,
        long peakHeapBytes,
        UUID runId,
        UUID indexVersionId
) {
}
