package com.ontoweave.data.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxProperties properties;
    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(
            OutboxProperties properties,
            OutboxEventRepository repository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.properties = properties;
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${ontoweave.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        if (!properties.enabled()) {
            return;
        }
        List<OutboxEventEntity> events = repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEventEntity event : events) {
            try {
                publish(event);
            } catch (Exception exception) {
                log.warn("failed to publish outbox event {} ({})", event.getId(), event.getEventType(), exception);
                return;
            }
        }
    }

    private void publish(OutboxEventEntity event) throws Exception {
        String envelope = objectMapper.writeValueAsString(envelope(event));
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                event.getEventType(),
                envelope,
                message -> {
                    message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    message.getMessageProperties().setMessageId(event.getId().toString());
                    return message;
                }
        );
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEventEntity managed = repository.findById(event.getId()).orElse(null);
            if (managed != null && managed.getPublishedAt() == null) {
                managed.markPublished(Instant.now());
            }
        });
    }

    private Map<String, Object> envelope(OutboxEventEntity event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", 1);
        envelope.put("eventId", event.getId());
        envelope.put("eventType", event.getEventType());
        envelope.put("occurredAt", event.getCreatedAt());
        envelope.put("payload", event.getPayload());
        return envelope;
    }
}
