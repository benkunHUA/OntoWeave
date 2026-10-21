package com.ontoweave.data.pipeline.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunStatus;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PipelineScheduleClaimer {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduleClaimer.class);

    private final PipelineScheduleRepository schedules;
    private final PipelineScheduleRunRepository runs;
    private final CronScheduleCalculator cron;
    private final ObjectMapper json;

    public PipelineScheduleClaimer(
            PipelineScheduleRepository schedules,
            PipelineScheduleRunRepository runs,
            CronScheduleCalculator cron,
            ObjectMapper json
    ) {
        this.schedules = schedules;
        this.runs = runs;
        this.cron = cron;
        this.json = json;
    }

    @Transactional
    public List<DueSchedule> claimDue(Instant now, int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0) {
            return List.of();
        }
        List<DueSchedule> claimed = new ArrayList<>();
        for (PipelineScheduleEntity schedule : schedules.lockDue(now, safeLimit)) {
            try {
                Instant dueAt = cron.latestDue(
                        schedule.getCronExpression(), schedule.getTimezone(),
                        schedule.getNextRunAt(), now
                );
                Instant nextRunAt = cron.next(
                        schedule.getCronExpression(), schedule.getTimezone(), now
                );
                ensurePending(schedule, dueAt);
                schedule.advance(nextRunAt, dueAt);
                schedules.save(schedule);
                claimed.add(new DueSchedule(schedule, dueAt));
            } catch (RuntimeException error) {
                log.warn("skipping invalid schedule {}: {}", schedule.getId(), error.getMessage());
            }
        }
        return claimed;
    }

    @Transactional
    public List<PendingSchedule> claimPending(int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0) {
            return List.of();
        }
        List<PendingSchedule> pending = new ArrayList<>();
        for (PipelineScheduleRunEntity run : runs.lockPending(safeLimit)) {
            schedules.findById(run.getScheduleId())
                    .ifPresent(schedule -> pending.add(
                            new PendingSchedule(schedule, run.getScheduledFor())
                    ));
        }
        return pending;
    }

    private void ensurePending(PipelineScheduleEntity schedule, Instant dueAt) {
        if (runs.findByScheduleIdAndScheduledFor(schedule.getId(), dueAt).isPresent()) {
            return;
        }
        boolean hasPending = runs.findFirstByScheduleIdAndStatusOrderByScheduledForAsc(
                schedule.getId(), PipelineScheduleRunStatus.PENDING
        ).isPresent();
        if (hasPending) {
            return;
        }
        runs.save(new PipelineScheduleRunEntity(
                UUID.randomUUID(),
                schedule.getId(),
                schedule.getPipelineId(),
                PipelineScheduleRunStatus.PENDING,
                schedule.getTriggerType(),
                dueAt,
                json.createArrayNode()
        ));
    }

    public record DueSchedule(PipelineScheduleEntity schedule, Instant dueAt) {
    }

    public record PendingSchedule(PipelineScheduleEntity schedule, Instant dueAt) {
    }
}
