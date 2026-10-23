package com.ontoweave.data.ontology.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class P3PerformanceSummaryWriter {

    private static final double NEAR_FASTEST_RATIO = 1.05;

    private P3PerformanceSummaryWriter() {
    }

    static Path write(ObjectMapper mapper, List<P3PerformanceResult> results, long requestedRows)
            throws Exception {
        Path directory = Path.of(System.getProperty(
                "p3.summary.dir", "target/p3-acceptance"));
        Files.createDirectories(directory);
        ObjectNode root = mapper.createObjectNode();
        root.put("commit", System.getProperty("p3.commit", "unknown"));
        root.put("javaVersion", System.getProperty("java.version"));
        root.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        root.put("requestedRows", requestedRows);
        root.put("postgresImage", "pgvector/pgvector:pg16");
        ArrayNode runs = root.putArray("runs");
        for (P3PerformanceResult result : results) {
            ObjectNode run = runs.addObject();
            run.put("batchSize", result.batchSize());
            run.put("rows", result.rows());
            run.put("totalMillis", result.totalMillis());
            run.put("readMillis", result.readMillis());
            run.put("writeMillis", result.writeMillis());
            run.put("listP95Millis", result.listP95Millis());
            run.put("filterP95Millis", result.filterP95Millis());
            run.put("filterRows", result.filterRows());
            run.put("instanceRows", result.instanceRows());
            run.put("valueRows", result.valueRows());
            run.put("peakHeapBytes", result.peakHeapBytes());
            run.put("runId", result.runId().toString());
            run.put("indexVersionId", result.indexVersionId().toString());
        }
        P3PerformanceResult fastest = results.stream()
                .min(java.util.Comparator.comparingDouble(P3PerformanceResult::totalMillis))
                .orElseThrow();
        P3PerformanceResult recommended = results.stream()
                .filter(result -> result.totalMillis()
                        <= fastest.totalMillis() * NEAR_FASTEST_RATIO)
                .min(java.util.Comparator
                        .comparingLong(P3PerformanceResult::peakHeapBytes)
                        .thenComparingDouble(P3PerformanceResult::totalMillis))
                .orElse(fastest);
        root.put("recommendedBatchSize", recommended.batchSize());
        root.put("recommendationBasis",
                "fastest within 5%, then lowest peak heap");
        Path file = directory.resolve("performance-summary.json");
        Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return file;
    }
}
