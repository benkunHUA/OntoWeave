package com.ontoweave.data.pipeline.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunStatus;
import com.ontoweave.data.pipeline.domain.PipelineScheduleStatus;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.execution.build.EnqueueScheduledBuild;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildService;
import com.ontoweave.data.pipeline.execution.build.PipelineInputResolver;
import com.ontoweave.data.pipeline.execution.build.PipelineResolvedInputs;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRunRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PipelineScheduleDispatcher {

    private final PipelineRepository pipelines;
    private final PipelineVersionRepository versions;
    private final PipelineInputResolver inputs;
    private final PipelineBuildRepository builds;
    private final PipelineBuildService buildService;
    private final PipelineScheduleRepository schedules;
    private final PipelineScheduleRunRepository runs;
    private final ObjectMapper json;
    private final PipelineScheduleMapper mapper = new PipelineScheduleMapper();

    public PipelineScheduleDispatcher(
            PipelineRepository pipelines,
            PipelineVersionRepository versions,
            PipelineInputResolver inputs,
            PipelineBuildRepository builds,
            PipelineBuildService buildService,
            PipelineScheduleRepository schedules,
            PipelineScheduleRunRepository runs,
            ObjectMapper json
    ) {
        this.pipelines = pipelines;
        this.versions = versions;
        this.inputs = inputs;
        this.builds = builds;
        this.buildService = buildService;
        this.schedules = schedules;
        this.runs = runs;
        this.json = json;
    }

    @Transactional
    public ScheduleRunResponse dispatch(PipelineScheduleEntity requested, Instant dueAt) {
        if (requested == null || requested.getId() == null || dueAt == null) {
            throw new IllegalArgumentException(PipelineScheduleConstants.ERROR_DISPATCH_INCOMPLETE);
        }
        PipelineScheduleEntity schedule = schedules.lockById(requested.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        PipelineScheduleConstants.ERROR_SCHEDULE_NOT_FOUND));
        PipelineScheduleRunEntity run = findOrCreateRun(schedule, dueAt);
        if (terminal(run)) {
            return mapper.toRunResponse(run);
        }
        if (schedule.getStatus() == PipelineScheduleStatus.PAUSED) {
            return finish(run, PipelineScheduleRunStatus.IGNORED,
                    PipelineScheduleConstants.REASON_SCHEDULE_PAUSED,
                    PipelineScheduleConstants.MESSAGE_PAUSED, Instant.now());
        }

        PipelineEntity pipeline = pipelines.findById(schedule.getPipelineId()).orElse(null);
        if (pipeline == null || pipeline.getCurrentVersionId() == null) {
            return finish(run, PipelineScheduleRunStatus.FAILED,
                    PipelineScheduleConstants.REASON_NOT_PUBLISHED,
                    PipelineScheduleConstants.MESSAGE_NOT_PUBLISHED, Instant.now());
        }
        PipelineVersionEntity version = versions
                .findByIdAndPipelineId(pipeline.getCurrentVersionId(), pipeline.getId())
                .orElse(null);
        if (version == null) {
            return finish(run, PipelineScheduleRunStatus.FAILED,
                    PipelineScheduleConstants.REASON_NOT_PUBLISHED,
                    PipelineScheduleConstants.MESSAGE_VERSION_NOT_FOUND, Instant.now());
        }

        List<ResolvedInput> resolved;
        try {
            resolved = inputs.resolve(version);
        } catch (RuntimeException error) {
            return finish(run, PipelineScheduleRunStatus.FAILED,
                    PipelineScheduleConstants.REASON_DISPATCH_FAILED,
                    PipelineScheduleConstants.MESSAGE_INPUT_RESOLUTION_FAILED, Instant.now());
        }
        String logicHash = ScheduleLogicHash.compute(version.getDefinitionHash(), resolved);
        run.evaluate(logicHash, PipelineResolvedInputs.toJson(json, resolved));
        if (unchanged(pipeline.getId(), version.getId(), logicHash)) {
            return finish(run, PipelineScheduleRunStatus.IGNORED,
                    PipelineScheduleConstants.REASON_NO_CHANGE,
                    PipelineScheduleConstants.MESSAGE_NO_CHANGE, Instant.now());
        }
        if (builds.existsByPipelineIdAndStatusIn(
                pipeline.getId(), PipelineScheduleConstants.ACTIVE_STATUSES
        )) {
            return pending(schedule, run, logicHash, resolved);
        }

        Instant effectiveDue = run.getScheduledFor();
        UUID operationId = ScheduleOperationIds.forRun(schedule.getId(), effectiveDue);
        try {
            UUID buildId = buildService.enqueueScheduled(new EnqueueScheduledBuild(
                    pipeline.getId(), version.getId(), schedule.getCreatedBy(), operationId,
                    resolved, logicHash
            ));
            Instant now = Instant.now();
            run.markDispatched(buildId, logicHash, PipelineResolvedInputs.toJson(json, resolved), now);
            schedule.recordRun(effectiveDue);
            schedules.save(schedule);
            return mapper.toRunResponse(runs.save(run));
        } catch (ApiException error) {
            if (PipelineBuildConstants.CODE_ACTIVE.equals(error.code())) {
                return pending(schedule, run, logicHash, resolved);
            }
            return finish(run, PipelineScheduleRunStatus.FAILED,
                    PipelineScheduleConstants.REASON_DISPATCH_FAILED,
                    error.getMessage(), Instant.now());
        }
    }

    private PipelineScheduleRunEntity findOrCreateRun(
            PipelineScheduleEntity schedule,
            Instant dueAt
    ) {
        Optional<PipelineScheduleRunEntity> sameTime = runs
                .findByScheduleIdAndScheduledFor(schedule.getId(), dueAt);
        if (sameTime.isPresent()) {
            return sameTime.get();
        }
        Optional<PipelineScheduleRunEntity> pending = runs
                .findFirstByScheduleIdAndStatusOrderByScheduledForAsc(
                        schedule.getId(), PipelineScheduleRunStatus.PENDING
                );
        return pending.orElseGet(() -> runs.save(new PipelineScheduleRunEntity(
                UUID.randomUUID(),
                schedule.getId(),
                schedule.getPipelineId(),
                PipelineScheduleRunStatus.PENDING,
                schedule.getTriggerType(),
                dueAt,
                json.createArrayNode()
        )));
    }

    private boolean unchanged(UUID pipelineId, UUID versionId, String logicHash) {
        return builds.findFirstByPipelineIdAndPipelineVersionIdAndStatusInOrderByCreatedAtDescIdDesc(
                        pipelineId, versionId, PipelineScheduleConstants.TERMINAL_STATUSES
                )
                .filter(build -> build.getStatus() == PipelineBuildStatus.SUCCEEDED)
                .map(PipelineBuildEntity::getLogicHash)
                .filter(logicHash::equals)
                .isPresent();
    }

    private ScheduleRunResponse pending(
            PipelineScheduleEntity schedule,
            PipelineScheduleRunEntity run,
            String logicHash,
            List<ResolvedInput> resolved
    ) {
        run.markPending(logicHash, PipelineResolvedInputs.toJson(json, resolved));
        schedule.recordRun(run.getScheduledFor());
        schedules.save(schedule);
        return mapper.toRunResponse(runs.save(run));
    }

    private ScheduleRunResponse finish(
            PipelineScheduleRunEntity run,
            PipelineScheduleRunStatus status,
            String reason,
            String message,
            Instant now
    ) {
        if (status == PipelineScheduleRunStatus.IGNORED) {
            run.markIgnored(reason, message, now);
        } else {
            run.markFailed(reason, message, now);
        }
        return mapper.toRunResponse(runs.save(run));
    }

    private boolean terminal(PipelineScheduleRunEntity run) {
        return run.getStatus() != null && run.getStatus() != PipelineScheduleRunStatus.PENDING;
    }
}
