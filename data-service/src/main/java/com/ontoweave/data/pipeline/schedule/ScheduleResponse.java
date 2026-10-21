package com.ontoweave.data.pipeline.schedule;

import java.time.Instant;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        UUID pipelineId,
        String name,
        String status,
        String triggerType,
        String cronExpression,
        String timezone,
        Instant nextRunAt,
        Instant lastRunAt,
        Instant pausedAt
) {
}
