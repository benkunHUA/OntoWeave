package com.ontoweave.data.pipeline.schedule;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final ThreadPoolExecutor workers;
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
        this.workers = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                capacity == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(capacity),
                new ScheduleThreadFactory(),
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
        int permits = reserveSlots();
        if (permits == 0) {
            return;
        }
        List<PipelineScheduleClaimer.DueSchedule> due;
        try {
            due = claimer.claimDue(Instant.now(), permits);
        } catch (RuntimeException error) {
            slots.release(permits);
            throw error;
        }
        if (due.size() < permits) {
            slots.release(permits - due.size());
        }
        for (PipelineScheduleClaimer.DueSchedule item : due) {
            try {
                workers.execute(() -> dispatch(item));
            } catch (RejectedExecutionException error) {
                slots.release();
                log.warn("schedule dispatch queue rejected {}", item.schedule().getId());
            }
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

    private void dispatch(PipelineScheduleClaimer.DueSchedule item) {
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
        workers.shutdownNow();
    }

    private static final class ScheduleThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "pipeline-schedule-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
