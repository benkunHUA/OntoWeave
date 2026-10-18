package com.ontoweave.data.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OutboxWriter {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository repository;

    public OutboxWriter(ObjectMapper objectMapper, OutboxEventRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    public OutboxEventEntity write(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        JsonNode json = payload instanceof JsonNode node ? node : objectMapper.valueToTree(payload);
        return repository.save(new OutboxEventEntity(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                json
        ));
    }
}
