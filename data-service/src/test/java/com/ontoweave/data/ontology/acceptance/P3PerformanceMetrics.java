package com.ontoweave.data.ontology.acceptance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class P3PerformanceMetrics {

    private P3PerformanceMetrics() {
    }

    static double p95Millis(List<Long> nanos) {
        if (nanos.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index) / 1_000_000.0;
    }

    static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    static final class HeapSampler implements AutoCloseable {

        private final Runtime runtime = Runtime.getRuntime();
        private final AtomicLong peak = new AtomicLong();
        private volatile boolean running;
        private Thread thread;

        void start() {
            running = true;
            sample();
            thread = new Thread(() -> {
                while (running) {
                    sample();
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "p3-heap-sampler");
            thread.setDaemon(true);
            thread.start();
        }

        long peakBytes() {
            return peak.get();
        }

        private void sample() {
            long used = runtime.totalMemory() - runtime.freeMemory();
            peak.accumulateAndGet(used, Math::max);
        }

        @Override
        public void close() {
            running = false;
            if (thread != null) {
                try {
                    thread.join(1_000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
