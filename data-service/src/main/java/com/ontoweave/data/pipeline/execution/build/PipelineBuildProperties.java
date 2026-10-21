package com.ontoweave.data.pipeline.execution.build;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ontoweave.pipeline.build")
public class PipelineBuildProperties {

    private boolean workerEnabled = true;
    private long pollIntervalMs = 2_000L;
    private long initialDelayMs = 3_000L;
    private Duration heartbeatInterval = Duration.ofSeconds(5);
    private Duration lostAfter = Duration.ofSeconds(30);
    private int maxPerPoll = 10;
    private int executorThreads = 2;
    private int executorQueueCapacity = 20;

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getLostAfter() {
        return lostAfter;
    }

    public void setLostAfter(Duration lostAfter) {
        this.lostAfter = lostAfter;
    }

    public int getMaxPerPoll() {
        return maxPerPoll;
    }

    public void setMaxPerPoll(int maxPerPoll) {
        this.maxPerPoll = maxPerPoll;
    }

    public int getExecutorThreads() {
        return executorThreads;
    }

    public void setExecutorThreads(int executorThreads) {
        this.executorThreads = executorThreads;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }
}
