package com.ontoweave.data.sync.execution;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SyncExecutionWorker {

    private final SyncRunExecutor executor;

    public SyncExecutionWorker(SyncRunExecutor executor) {
        this.executor = executor;
    }

    @RabbitListener(queues = "${ontoweave.sync.command-queue:sync.execute}")
    public void execute(String runId) {
        executor.execute(UUID.fromString(runId.trim()));
    }
}
