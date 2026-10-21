package com.ontoweave.data.pipeline.execution.preview;

import java.util.concurrent.Future;

final class PreviewLease {

    private volatile Future<?> future;
    private volatile boolean canceled;
    private volatile boolean detached;
    private volatile boolean finished;

    boolean isCanceled() {
        return canceled;
    }

    boolean isStopped() {
        return canceled || detached;
    }

    boolean isFinished() {
        return finished;
    }

    boolean hasTask() {
        return future != null;
    }

    void attach(Future<?> future) {
        boolean cancel;
        synchronized (this) {
            this.future = future;
            cancel = isStopped();
        }
        if (cancel) {
            future.cancel(true);
        }
    }

    void cancelTask() {
        Future<?> current;
        synchronized (this) {
            current = future;
        }
        if (current != null) {
            current.cancel(true);
        }
    }

    void markCanceled() {
        synchronized (this) {
            canceled = true;
        }
    }

    void markFinished() {
        finished = true;
    }

    void detach() {
        Future<?> current;
        synchronized (this) {
            detached = true;
            current = future;
        }
        if (current != null) {
            current.cancel(true);
        }
    }
}
