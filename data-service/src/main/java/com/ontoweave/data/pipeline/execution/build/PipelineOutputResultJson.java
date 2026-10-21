package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.execution.output.WrittenSnapshot;

import java.util.List;

public final class PipelineOutputResultJson {

    private PipelineOutputResultJson() {
    }

    public static ObjectNode result(
            ObjectMapper mapper,
            WrittenSnapshot snapshot,
            List<ExpectationResult> expectations
    ) {
        ObjectNode result = mapper.createObjectNode();
        if (snapshot != null) {
            ObjectNode value = result.putObject("snapshot");
            value.put("icebergCatalog", snapshot.icebergCatalog());
            value.put("icebergNamespace", snapshot.icebergNamespace());
            value.put("icebergTable", snapshot.icebergTable());
            value.put("branch", snapshot.branch());
            value.put("icebergSnapshotId", snapshot.icebergSnapshotId());
            value.set("schema", snapshot.schema());
            value.put("rowCount", snapshot.rowCount());
            value.put("byteSize", snapshot.byteSize());
            value.put("storagePath", snapshot.storagePath());
            value.put("committedAt", snapshot.committedAt() == null
                    ? java.time.Instant.now().toString() : snapshot.committedAt().toString());
        }
        expectations.forEach(item -> result.withArray("expectations")
                .add(mapper.valueToTree(item)));
        return result;
    }

    public static ObjectNode stats(ObjectMapper mapper, WrittenSnapshot snapshot, long elapsedMs) {
        ObjectNode stats = mapper.createObjectNode();
        if (snapshot != null) {
            stats.put("rowCount", snapshot.rowCount());
            stats.put("byteSize", snapshot.byteSize());
            stats.put("icebergSnapshotId", snapshot.icebergSnapshotId());
            if (snapshot.engineJobId() != null) {
                stats.put("engineJobId", snapshot.engineJobId());
            }
        }
        stats.put("elapsedMs", elapsedMs);
        return stats;
    }
}
