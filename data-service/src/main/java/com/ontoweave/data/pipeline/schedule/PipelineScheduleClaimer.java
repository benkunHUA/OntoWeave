package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PipelineScheduleClaimer {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduleClaimer.class);

    private final PipelineScheduleRepository schedules;
    private final CronScheduleCalculator cron;

    public PipelineScheduleClaimer(
            PipelineScheduleRepository schedules,
            CronScheduleCalculator cron
    ) {
        this.schedules = schedules;
        this.cron = cron;
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
                schedule.advance(nextRunAt, dueAt);
                schedules.save(schedule);
                claimed.add(new DueSchedule(schedule, dueAt));
            } catch (RuntimeException error) {
                log.warn("skipping invalid schedule {}: {}", schedule.getId(), error.getMessage());
            }
        }
        return claimed;
    }

    public record DueSchedule(PipelineScheduleEntity schedule, Instant dueAt) {
    }
}
