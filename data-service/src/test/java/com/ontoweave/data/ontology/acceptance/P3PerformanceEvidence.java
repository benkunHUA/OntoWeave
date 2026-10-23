package com.ontoweave.data.ontology.acceptance;

final class P3PerformanceEvidence {

    private P3PerformanceEvidence() {
    }

    static void log(P3PerformanceResult result) {
        System.out.printf(
                "P3_PERF batch=%d rows=%d totalMs=%.3f readMs=%.3f writeMs=%.3f "
                        + "listP95Ms=%.3f filterP95Ms=%.3f instances=%d values=%d heapPeak=%d%n",
                result.batchSize(), result.rows(), result.totalMillis(), result.readMillis(),
                result.writeMillis(), result.listP95Millis(), result.filterP95Millis(),
                result.instanceRows(), result.valueRows(), result.peakHeapBytes());
    }
}
