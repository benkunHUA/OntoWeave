package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PipelineScheduleService {

    ScheduleResponse get(UUID pipelineId, UUID ownerId);

    ScheduleResponse put(UUID pipelineId, UUID ownerId, ScheduleRequest request);

    ScheduleResponse pause(UUID pipelineId, UUID ownerId);

    ScheduleResponse resume(UUID pipelineId, UUID ownerId);

    List<ScheduleRunResponse> runs(UUID pipelineId, UUID ownerId, int limit, int offset);

    ScheduleRunResponse dispatch(PipelineScheduleEntity schedule, Instant dueAt);
}
