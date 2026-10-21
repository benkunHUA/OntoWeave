package com.ontoweave.data.pipeline.execution.preview;

import java.time.Duration;

final class PreviewDeadline {

    private final long startedAt;
    private final long deadline;

    private PreviewDeadline(long startedAt, long deadline) {
        this.startedAt = startedAt;
        this.deadline = deadline;
    }

    static PreviewDeadline start(Duration timeout) {
        long now = System.nanoTime();
        return new PreviewDeadline(now, now + timeout.toNanos());
    }

    boolean expired() {
        return System.nanoTime() >= deadline;
    }

    Duration remaining() {
        long remaining = Math.max(0L, deadline - System.nanoTime());
        return Duration.ofNanos(remaining);
    }

    long elapsedMs() {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
