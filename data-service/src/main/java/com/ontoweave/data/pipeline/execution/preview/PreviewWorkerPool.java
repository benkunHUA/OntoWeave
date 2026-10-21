package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class PreviewWorkerPool {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private PreviewWorkerPool() {
    }

    static ThreadPoolExecutor create() {
        return new ThreadPoolExecutor(
                0,
                PipelineExecutionLimits.MAX_PREVIEW_WORKERS,
                PipelineExecutionLimits.PREVIEW_WORKER_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                task -> new Thread(task, "preview-worker-" + SEQUENCE.incrementAndGet())
        );
    }
}
