package com.ontoweave.data.ontology.index;

import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class IndexEventPublisher {

    public static final String AGGREGATE_TYPE = "ontology_index_run";
    public static final String STARTED = "ontology.index.run.started";
    public static final String SUCCEEDED = "ontology.index.run.succeeded";
    public static final String FAILED = "ontology.index.run.failed";
    public static final String CANCELLED = "ontology.index.run.cancelled";

    private final OutboxWriter outbox;

    public IndexEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void started(OntologyIndexRunEntity run) {
        write(run, STARTED, "RUNNING", Map.of(), null);
    }

    public void succeeded(OntologyIndexRunEntity run, Map<String, Object> stats) {
        write(run, SUCCEEDED, "SUCCEEDED", stats, null);
    }

    public void failed(
            OntologyIndexRunEntity run,
            String status,
            String errorCode,
            Map<String, Object> stats
    ) {
        write(run, FAILED, status, stats, errorCode);
    }

    public void cancelled(OntologyIndexRunEntity run, Map<String, Object> stats) {
        write(run, CANCELLED, "CANCELLED", stats, null);
    }

    private void write(
            OntologyIndexRunEntity run,
            String eventType,
            String status,
            Map<String, Object> stats,
            String errorCode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.getId());
        payload.put("subjectKind", run.getSubjectKind());
        payload.put("subjectId", run.getSubjectId());
        payload.put("status", status);
        if (stats != null && !stats.isEmpty()) {
            payload.put("counts", stats);
        }
        if (errorCode != null) {
            payload.put("errorCode", errorCode);
        }
        outbox.write(AGGREGATE_TYPE, run.getId(), eventType, payload);
    }
}
