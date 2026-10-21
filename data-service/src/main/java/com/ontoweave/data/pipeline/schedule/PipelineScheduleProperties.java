package com.ontoweave.data.pipeline.schedule;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ontoweave.pipeline.schedule")
public class PipelineScheduleProperties {

    private boolean workerEnabled = true;
    private long pollIntervalMs = 15_000L;
    private long initialDelayMs = 15_000L;
    private int maxPerPoll = 100;
    private int scanThreads = 1;
    private int scanQueueCapacity = 1;
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

    public int getMaxPerPoll() {
        return maxPerPoll;
    }

    public void setMaxPerPoll(int maxPerPoll) {
        this.maxPerPoll = maxPerPoll;
    }

    public int getScanThreads() {
        return scanThreads;
    }

    public void setScanThreads(int scanThreads) {
        this.scanThreads = scanThreads;
    }

    public int getScanQueueCapacity() {
        return scanQueueCapacity;
    }

    public void setScanQueueCapacity(int scanQueueCapacity) {
        this.scanQueueCapacity = scanQueueCapacity;
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
