package com.ontoweave.data.outbox;

import com.ontoweave.data.common.config.OutboxProperties;
import com.ontoweave.data.common.config.SyncProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange dataEventsExchange(OutboxProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    DirectExchange syncCommandExchange(SyncProperties properties) {
        return new DirectExchange(properties.commandExchange(), true, false);
    }

    @Bean
    Queue syncExecuteQueue(SyncProperties properties) {
        return new Queue(properties.commandQueue(), true);
    }

    @Bean
    Binding syncExecuteBinding(Queue syncExecuteQueue, DirectExchange syncCommandExchange) {
        return BindingBuilder.bind(syncExecuteQueue).to(syncCommandExchange).with("sync.execute");
    }
}
