package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.outbox.OutboxWriter;

import java.util.UUID;

import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.ACTOR_TYPE_USER;
import static com.ontoweave.data.pipeline.draft.DraftConstants.EVENT_AGGREGATE_TYPE;
import static com.ontoweave.data.pipeline.draft.DraftConstants.EVENT_DRAFT_CHANGED;

final class PipelineDraftEventWriter {

    private final ObjectMapper mapper;
    private final OutboxWriter outbox;

    PipelineDraftEventWriter(ObjectMapper mapper, OutboxWriter outbox) {
        this.mapper = mapper;
        this.outbox = outbox;
    }

    void changed(UUID pipelineId, long revision, UUID operationId, UUID turnId, String opType) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("pipelineId", pipelineId.toString());
        payload.put("revision", revision);
        payload.put("operationId", operationId.toString());
        if (turnId == null) {
            payload.putNull("turnId");
        } else {
            payload.put("turnId", turnId.toString());
        }
        payload.put("actorType", ACTOR_TYPE_USER);
        payload.put("opType", opType);
        outbox.write(EVENT_AGGREGATE_TYPE, pipelineId, EVENT_DRAFT_CHANGED, payload);
    }
}
