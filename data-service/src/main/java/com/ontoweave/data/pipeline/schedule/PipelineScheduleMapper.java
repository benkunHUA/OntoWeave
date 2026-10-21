package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PipelineScheduleMapper {

    public ScheduleResponse toResponse(PipelineScheduleEntity schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getPipelineId(),
                schedule.getName(),
                lower(schedule.getStatus() == null ? null : schedule.getStatus().name()),
                schedule.getTriggerType(),
                schedule.getCronExpression(),
                schedule.getTimezone(),
                schedule.getNextRunAt(),
                schedule.getLastRunAt(),
                schedule.getPausedAt()
        );
    }

    public ScheduleRunResponse toRunResponse(PipelineScheduleRunEntity run) {
        return new ScheduleRunResponse(
                run.getId(),
                run.getScheduleId(),
                run.getPipelineId(),
                lower(run.getStatus() == null ? null : run.getStatus().name()),
                run.getTriggerType(),
                run.getScheduledFor(),
                run.getBuildId(),
                run.getLogicHash(),
                run.getReasonCode(),
                run.getMessage(),
                run.getStartedAt(),
                run.getFinishedAt()
        );
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
