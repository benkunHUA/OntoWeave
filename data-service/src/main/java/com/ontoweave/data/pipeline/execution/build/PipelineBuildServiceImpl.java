package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.check.PipelineCheckResult;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineBuildTrigger;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import com.ontoweave.data.pipeline.service.PipelineErrors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PipelineBuildServiceImpl implements PipelineBuildService {

    private final PipelineRepository pipelines;
    private final PipelineVersionRepository versions;
    private final PipelineCheckService checks;
    private final PipelineVersionFreezer freezer;
    private final PipelineBuildRepository builds;
    private final PipelineBuildOutputRepository outputs;
    private final PipelineBuildCreator creator;
    private final PipelineBuildMapper mapper;
    private final ObjectMapper json;

    public PipelineBuildServiceImpl(
            PipelineRepository pipelines,
            PipelineVersionRepository versions,
            PipelineCheckService checks,
            PipelineVersionFreezer freezer,
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository outputs,
            PipelineBuildCreator creator,
            PipelineBuildMapper mapper,
            ObjectMapper json
    ) {
        this.pipelines = pipelines;
        this.versions = versions;
        this.checks = checks;
        this.freezer = freezer;
        this.builds = builds;
        this.outputs = outputs;
        this.creator = creator;
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public BuildResponse enqueue(UUID pipelineId, UUID ownerId, CreateBuildRequest request) {
        requireActiveOwned(pipelineId, ownerId);
        requireOperation(request);
        Optional<PipelineBuildEntity> existing = builds
                .findByPipelineIdAndOperationId(pipelineId, request.operationId());
        if (existing.isPresent()) {
            return response(existing.get());
        }

        PipelineCheckResult check = checks.check(pipelineId, ownerId, request.draftRevision());
        if (!check.blockingIssues().isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    PipelineBuildConstants.CODE_NOT_READY,
                    "流程存在阻塞错误，无法构建",
                    java.util.Map.of("issues", check.blockingIssues())
            );
        }
        requireNoActiveBuild(pipelineId);

        FrozenBuild frozen = freezer.freeze(pipelineId, ownerId, request.draftRevision());
        PipelineBuildEntity build = new PipelineBuildEntity(
                UUID.randomUUID(),
                pipelineId,
                frozen.version().getId(),
                PipelineBuildStatus.QUEUED,
                PipelineBuildTrigger.MANUAL,
                request.operationId(),
                ownerId,
                PipelineResolvedInputs.toJson(json, frozen.resolvedInputs()),
                json.createObjectNode()
        );
        try {
            return response(creator.create(build, frozen.version()));
        } catch (DataIntegrityViolationException error) {
            return recoverConflict(pipelineId, request.operationId(), error);
        }
    }

    @Override
    @Transactional(
            propagation = Propagation.REQUIRED,
            noRollbackFor = ApiException.class
    )
    public UUID enqueueScheduled(EnqueueScheduledBuild command) {
        if (command == null || command.pipelineId() == null
                || command.pipelineVersionId() == null || command.operationId() == null) {
            throw invalid("调度构建参数不完整");
        }
        requireActivePipeline(command.pipelineId());
        Optional<PipelineBuildEntity> existing = builds
                .findByPipelineIdAndOperationId(command.pipelineId(), command.operationId());
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        requireNoActiveBuild(command.pipelineId());
        PipelineVersionEntity version = versions
                .findByIdAndPipelineId(command.pipelineVersionId(), command.pipelineId())
                .orElseThrow(() -> notFound("流程版本不存在"));

        PipelineBuildEntity build = new PipelineBuildEntity(
                UUID.randomUUID(),
                command.pipelineId(),
                command.pipelineVersionId(),
                PipelineBuildStatus.QUEUED,
                PipelineBuildTrigger.SCHEDULE,
                command.operationId(),
                command.requestedBy(),
                PipelineResolvedInputs.toJson(json, command.resolvedInputs()),
                json.createObjectNode()
        );
        build.updateLogicHash(command.logicHash());
        return creator.createInCurrentTransaction(build, version).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public BuildResponse get(UUID pipelineId, UUID buildId, UUID ownerId) {
        requireReadableOwned(pipelineId, ownerId);
        PipelineBuildEntity build = builds.findByIdAndPipelineId(buildId, pipelineId)
                .orElseThrow(() -> notFound("构建不存在"));
        return response(build);
    }

    @Override
    @Transactional
    public void requestCancel(UUID pipelineId, UUID buildId, UUID ownerId) {
        requireActiveOwned(pipelineId, ownerId);
        PipelineBuildEntity build = builds.lockByIdAndPipelineId(buildId, pipelineId)
                .orElseThrow(() -> notFound("构建不存在"));
        if (build.getStatus() != PipelineBuildStatus.QUEUED
                && build.getStatus() != PipelineBuildStatus.RUNNING) {
            return;
        }
        build.requestCancel(Instant.now());
        builds.save(build);
    }

    private BuildResponse recoverConflict(
            UUID pipelineId,
            UUID operationId,
            DataIntegrityViolationException error
    ) {
        Optional<PipelineBuildEntity> existing = builds
                .findByPipelineIdAndOperationId(pipelineId, operationId);
        if (existing.isPresent()) {
            return response(existing.get());
        }
        if (builds.existsByPipelineIdAndStatusIn(
                pipelineId, PipelineBuildConstants.ACTIVE_STATUSES
        )) {
            throw activeBuild();
        }
        throw error;
    }

    private BuildResponse response(PipelineBuildEntity build) {
        return mapper.toResponse(
                build,
                outputs.findByBuildIdOrderByCreatedAtAsc(build.getId())
        );
    }

    private void requireReadableOwned(UUID pipelineId, UUID ownerId) {
        if (pipelineId == null || ownerId == null
                || pipelines.findByIdAndOwnerId(pipelineId, ownerId).isEmpty()) {
            throw notFound("流程不存在");
        }
    }

    private void requireActiveOwned(UUID pipelineId, UUID ownerId) {
        if (pipelineId == null || ownerId == null) {
            throw notFound("流程不存在");
        }
        if (pipelines.findByIdAndOwnerIdAndArchivedAtIsNull(pipelineId, ownerId).isPresent()) {
            return;
        }
        if (pipelines.findByIdAndOwnerId(pipelineId, ownerId).isPresent()) {
            throw PipelineErrors.archived();
        }
        throw notFound("流程不存在");
    }

    private void requireActivePipeline(UUID pipelineId) {
        if (pipelines.findByIdAndArchivedAtIsNull(pipelineId).isPresent()) {
            return;
        }
        if (pipelines.existsById(pipelineId)) {
            throw PipelineErrors.archived();
        }
        throw notFound("流程不存在");
    }

    private void requireNoActiveBuild(UUID pipelineId) {
        if (builds.existsByPipelineIdAndStatusIn(
                pipelineId, PipelineBuildConstants.ACTIVE_STATUSES
        )) {
            throw activeBuild();
        }
    }

    private void requireOperation(CreateBuildRequest request) {
        if (request == null || request.operationId() == null) {
            throw invalid("operationId 不能为空");
        }
    }

    private ApiException activeBuild() {
        return new ApiException(
                HttpStatus.CONFLICT,
                PipelineBuildConstants.CODE_ACTIVE,
                "流程已有排队或运行中的构建"
        );
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, PipelineBuildConstants.CODE_NOT_FOUND, message);
    }
}
