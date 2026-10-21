package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunStatus;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PipelineScheduleServiceImpl implements PipelineScheduleService {

    private final PipelineRepository pipelines;
    private final PipelineScheduleRepository schedules;
    private final PipelineScheduleRunRepository runs;
    private final PipelineScheduleDispatcher dispatcher;
    private final CronScheduleCalculator cron;
    private final PipelineScheduleMapper mapper;

    public PipelineScheduleServiceImpl(
            PipelineRepository pipelines,
            PipelineScheduleRepository schedules,
            PipelineScheduleRunRepository runs,
            PipelineScheduleDispatcher dispatcher,
            CronScheduleCalculator cron,
            PipelineScheduleMapper mapper
    ) {
        this.pipelines = pipelines;
        this.schedules = schedules;
        this.runs = runs;
        this.dispatcher = dispatcher;
        this.cron = cron;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse get(UUID pipelineId, UUID ownerId) {
        requireOwned(pipelineId, ownerId);
        return mapper.toResponse(schedules.findByPipelineId(pipelineId)
                .orElseThrow(() -> notFound(PipelineScheduleConstants.ERROR_SCHEDULE_NOT_FOUND)));
    }

    @Override
    @Transactional
    public ScheduleResponse put(UUID pipelineId, UUID ownerId, ScheduleRequest request) {
        requireOwned(pipelineId, ownerId);
        if (request == null || blank(request.cronExpression()) || blank(request.timezone())) {
            throw invalid(PipelineScheduleConstants.ERROR_CRON_REQUIRED);
        }
        try {
            Instant nextRunAt = cron.next(request.cronExpression(), request.timezone(), Instant.now());
            String name = blank(request.name())
                    ? PipelineScheduleConstants.DEFAULT_NAME : request.name().trim();
            PipelineScheduleEntity schedule = schedules.lockByPipelineId(pipelineId)
                    .map(existing -> {
                        existing.updateDefinition(name, request.cronExpression().trim(),
                                request.timezone().trim(), nextRunAt);
                        return existing;
                    })
                    .orElseGet(() -> new PipelineScheduleEntity(
                            UUID.randomUUID(), pipelineId, name,
                            request.cronExpression().trim(), request.timezone().trim(),
                            nextRunAt, ownerId
                    ));
            return mapper.toResponse(schedules.save(schedule));
        } catch (IllegalArgumentException error) {
            throw invalid(error.getMessage());
        }
    }

    @Override
    @Transactional
    public ScheduleResponse pause(UUID pipelineId, UUID ownerId) {
        requireOwned(pipelineId, ownerId);
        PipelineScheduleEntity schedule = schedules.lockByPipelineId(pipelineId)
                .orElseThrow(() -> notFound(PipelineScheduleConstants.ERROR_SCHEDULE_NOT_FOUND));
        Instant now = Instant.now();
        schedule.pause(ownerId, now);
        for (PipelineScheduleRunEntity pending : runs.lockPendingByScheduleId(schedule.getId())) {
            if (pending.getStatus() == PipelineScheduleRunStatus.PENDING) {
                pending.markIgnored(PipelineScheduleConstants.REASON_SCHEDULE_PAUSED,
                        PipelineScheduleConstants.MESSAGE_PAUSED, now);
                runs.save(pending);
            }
        }
        return mapper.toResponse(schedules.save(schedule));
    }

    @Override
    @Transactional
    public ScheduleResponse resume(UUID pipelineId, UUID ownerId) {
        requireOwned(pipelineId, ownerId);
        PipelineScheduleEntity schedule = schedules.lockByPipelineId(pipelineId)
                .orElseThrow(() -> notFound(PipelineScheduleConstants.ERROR_SCHEDULE_NOT_FOUND));
        try {
            Instant nextRunAt = cron.next(schedule.getCronExpression(), schedule.getTimezone(), Instant.now());
            schedule.resume(nextRunAt, Instant.now());
            return mapper.toResponse(schedules.save(schedule));
        } catch (IllegalArgumentException error) {
            throw invalid(error.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleRunResponse> runs(UUID pipelineId, UUID ownerId, int limit, int offset) {
        requireOwned(pipelineId, ownerId);
        int safeLimit = Math.min(Math.max(limit, 1), PipelineScheduleConstants.MAX_PAGE_SIZE);
        int safeOffset = Math.max(offset, 0);
        return runs.findPage(pipelineId, safeLimit, safeOffset)
                .stream()
                .map(mapper::toRunResponse)
                .toList();
    }

    @Override
    public ScheduleRunResponse dispatch(PipelineScheduleEntity schedule, Instant dueAt) {
        return dispatcher.dispatch(schedule, dueAt);
    }

    private PipelineEntity requireOwned(UUID pipelineId, UUID ownerId) {
        if (pipelineId == null || ownerId == null) {
            throw notFound(PipelineScheduleConstants.ERROR_PIPELINE_NOT_FOUND);
        }
        return pipelines.findByIdAndOwnerId(pipelineId, ownerId)
                .orElseThrow(() -> notFound(PipelineScheduleConstants.ERROR_PIPELINE_NOT_FOUND));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, PipelineBuildConstants.CODE_NOT_FOUND, message);
    }
}
