package com.ontoweave.data.ontology.acceptance;

import com.ontoweave.data.ontology.store.ObjectStoreWriter;
import com.ontoweave.data.ontology.store.StageValidation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

final class P3TimedObjectStoreWriter implements ObjectStoreWriter {

    private final ObjectStoreWriter delegate;
    private final LongAdder writeNanos = new LongAdder();

    P3TimedObjectStoreWriter(ObjectStoreWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(UUID runId, List<ObjectStageRow> rows) {
        time(() -> delegate.write(runId, rows));
    }

    @Override
    public StageValidation validate(UUID runId) {
        return delegate.validate(runId);
    }

    @Override
    public void promote(UUID runId, UUID versionId) {
        time(() -> delegate.promote(runId, versionId));
    }

    @Override
    public void deleteStage(UUID runId) {
        time(() -> delegate.deleteStage(runId));
    }

    @Override
    public void deleteVersion(UUID versionId) {
        time(() -> delegate.deleteVersion(versionId));
    }

    long writeNanos() {
        return writeNanos.sum();
    }

    private void time(Runnable action) {
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            writeNanos.add(System.nanoTime() - start);
        }
    }
}
