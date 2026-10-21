package com.ontoweave.data.pipeline.schedule;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "ontoweave.pipeline.schedule",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PipelineScheduleWorker {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduleWorker.class);

    private final PipelineScheduleClaimer claimer;
    private final PipelineScheduleService schedules;
    private final PipelineScheduleProperties properties;
    private final Semaphore slots;
    private final ThreadPoolExecutor scanners;
    private final ThreadPoolExecutor workers;
    private final AtomicBoolean scanScheduled = new AtomicBoolean();
    private volatile boolean shutdown;

    public PipelineScheduleWorker(
            PipelineScheduleClaimer claimer,
            PipelineScheduleService schedules,
            PipelineScheduleProperties properties
    ) {
        this.claimer = claimer;
        this.schedules = schedules;
        this.properties = properties;
        int threads = Math.max(1, properties.getExecutorThreads());
        int capacity = Math.max(0, properties.getExecutorQueueCapacity());
        this.slots = new Semaphore(threads + capacity);
        int scanThreads = Math.max(1, properties.getScanThreads());
        this.scanners = new ThreadPoolExecutor(
                scanThreads,
                scanThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.getScanQueueCapacity())),
                new ScheduleThreadFactory("pipeline-schedule-scan-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.workers = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                capacity == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(capacity),
                new ScheduleThreadFactory("pipeline-schedule-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Scheduled(
            fixedDelayString = "${ontoweave.pipeline.schedule.poll-interval-ms:15000}",
            initialDelayString = "${ontoweave.pipeline.schedule.initial-delay-ms:15000}"
    )
    public void poll() {
        if (shutdown) {
            return;
        }
        if (!scanScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            scanners.execute(this::scan);
        } catch (RejectedExecutionException error) {
            scanScheduled.set(false);
            log.warn("schedule scan rejected", error);
        }
    }

    private void scan() {
        try {
            claimAndSubmit();
        } catch (RuntimeException error) {
            log.warn("schedule scan failed", error);
        } finally {
            scanScheduled.set(false);
        }
    }

    private void claimAndSubmit() {
        int permits = reserveSlots();
        if (permits == 0) {
            return;
        }
        try {
            claimer.claimDue(Instant.now(), permits);
            List<PipelineScheduleClaimer.PendingSchedule> pending = claimer.claimPending(permits);
            int submitted = 0;
            for (PipelineScheduleClaimer.PendingSchedule item : pending) {
                if (submitted >= permits) {
                    break;
                }
                try {
                    workers.execute(() -> dispatch(item));
                    submitted++;
                } catch (RejectedExecutionException error) {
                    log.warn("schedule dispatch queue rejected {}", item.schedule().getId());
                }
            }
            if (submitted < permits) {
                slots.release(permits - submitted);
            }
        } catch (RuntimeException error) {
            slots.release(permits);
            throw error;
        }
    }

    private int reserveSlots() {
        int reserved = 0;
        for (int index = 0; index < properties.getMaxPerPoll(); index++) {
            if (!slots.tryAcquire()) {
                break;
            }
            reserved++;
        }
        return reserved;
    }

    private void dispatch(PipelineScheduleClaimer.PendingSchedule item) {
        try {
            schedules.dispatch(item.schedule(), item.dueAt());
        } catch (RuntimeException error) {
            log.warn("scheduled dispatch failed for {}", item.schedule().getId(), error);
        } finally {
            slots.release();
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown = true;
        scanners.shutdownNow();
        workers.shutdownNow();
    }

    private static final class ScheduleThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();
        private final String prefix;

        private ScheduleThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
