package com.ontoweave.data.pipeline.schedule;

import java.time.Instant;
import java.util.UUID;

public record ScheduleRunResponse(
        UUID id,
        UUID scheduleId,
        UUID pipelineId,
        String status,
        String triggerType,
        Instant scheduledFor,
        UUID buildId,
        String logicHash,
        String reasonCode,
        String message,
        Instant startedAt,
        Instant finishedAt
) {
}
