package com.ontoweave.data.sync.execution;

import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SyncCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SyncCommandDispatcher.class);

    private final SyncProperties properties;
    private final SyncRunRepository runs;
    private final RabbitTemplate rabbitTemplate;

    public SyncCommandDispatcher(
            SyncProperties properties,
            SyncRunRepository runs,
            RabbitTemplate rabbitTemplate
    ) {
        this.properties = properties;
        this.runs = runs;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${ontoweave.sync.dispatch-interval-ms:2000}")
    public void dispatchQueued() {
        if (!properties.dispatchEnabled()) {
            return;
        }
        for (SyncRunEntity run : runs
                .findTop20ByStatusAndDispatchedAtIsNullAndDispatchAttemptsLessThanOrderByCreatedAtAsc(
                        SyncRunStatus.QUEUED,
                        properties.maxDispatchAttempts()
                )) {
            if (runs.beginDispatch(run.getId(), properties.maxDispatchAttempts()) != 1) {
                continue;
            }
            CorrelationData correlation = new CorrelationData(run.getId().toString());
            try {
                rabbitTemplate.convertAndSend(
                        properties.commandExchange(),
                        "sync.execute",
                        run.getId().toString(),
                        message -> {
                            message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
                            message.getMessageProperties().setMessageId(run.getId().toString());
                            message.getMessageProperties().setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE);
                            return message;
                        },
                        correlation
                );
                correlation.getFuture().whenComplete((confirm, error) -> {
                    if (error == null && confirm != null && confirm.isAck()) {
                        runs.confirmDispatched(run.getId(), Instant.now());
                    } else {
                        log.warn("rabbitmq did not confirm sync run {}: {}", run.getId(), error);
                    }
                });
            } catch (Exception exception) {
                log.warn("failed to dispatch sync run {}", run.getId(), exception);
            }
        }
    }
}
