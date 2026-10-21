package com.ontoweave.data.pipeline.execution.preview;

import java.util.concurrent.atomic.AtomicBoolean;

final class PreviewEpochTicket implements AutoCloseable {

    private final long epoch;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();

    PreviewEpochTicket(long epoch, Runnable onClose) {
        this.epoch = epoch;
        this.onClose = onClose;
    }

    long epoch() {
        return epoch;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            onClose.run();
        }
    }
}
