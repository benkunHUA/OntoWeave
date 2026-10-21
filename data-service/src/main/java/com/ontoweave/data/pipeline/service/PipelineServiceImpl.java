package com.ontoweave.data.pipeline.service;

import com.ontoweave.data.pipeline.api.dto.CopyPipelineRequest;
import com.ontoweave.data.pipeline.api.dto.PipelinePageResponse;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.EMPTY_DRAFT;
import static com.ontoweave.data.pipeline.service.PipelineErrors.bindingConflict;
import static com.ontoweave.data.pipeline.service.PipelineErrors.notFound;
import static com.ontoweave.data.pipeline.service.PipelineErrors.validation;

@Service
public class PipelineServiceImpl implements PipelineService {

    private final PipelineRepository pipelines;
    private final PipelineDraftRepository drafts;
    private final PipelineWriteGuard writeGuard;
    private final PipelineCopyService copyService;

    public PipelineServiceImpl(
            PipelineRepository pipelines,
            PipelineDraftRepository drafts,
            PipelineWriteGuard writeGuard,
            PipelineCopyService copyService
    ) {
        this.pipelines = pipelines;
        this.drafts = drafts;
        this.writeGuard = writeGuard;
        this.copyService = copyService;
    }

    @Override
    @Transactional
    public PipelineCreateResult create(CreatePipelineRequest request, UUID operationId) {
        requireCreateRequest(request);
        String name = PipelineNames.require(request.name());
        PipelineEntity existing = pipelines.findById(request.pipelineId()).orElse(null);
        if (existing != null) {
            requireSameBinding(existing, request.ownerId(), request.conversationId());
            ensureDraft(request.pipelineId(), request.ownerId());
            return new PipelineCreateResult(response(existing), false);
        }
        PipelineEntity conversationOwner = pipelines.findByConversationId(request.conversationId())
                .orElse(null);
        if (conversationOwner != null) {
            if (!Objects.equals(conversationOwner.getId(), request.pipelineId())) {
                throw bindingConflict();
            }
            requireSameBinding(conversationOwner, request.ownerId(), request.conversationId());
            ensureDraft(conversationOwner.getId(), conversationOwner.getOwnerId());
            return new PipelineCreateResult(response(conversationOwner), false);
        }
        int inserted = pipelines.insertIfAbsent(
                request.pipelineId(),
                request.ownerId(),
                request.conversationId(),
                name
        );
        if (inserted == 0) {
            PipelineEntity raced = pipelines.findById(request.pipelineId()).orElse(null);
            if (raced != null) {
                requireSameBinding(raced, request.ownerId(), request.conversationId());
                ensureDraft(raced.getId(), raced.getOwnerId());
                return new PipelineCreateResult(response(raced), false);
            }
            throw bindingConflict();
        }
        ensureDraft(request.pipelineId(), request.ownerId());
        PipelineEntity created = pipelines.findById(request.pipelineId())
                .orElseThrow(() -> notFound("流程不存在"));
        return new PipelineCreateResult(response(created), true);
    }

    @Override
    @Transactional(readOnly = true)
    public PipelinePageResponse list(UUID ownerId, int limit, int offset) {
        if (ownerId == null || limit < 1 || limit > PipelineServiceConstants.MAX_LIMIT || offset < 0) {
            throw validation("分页参数不合法");
        }
        List<PipelineEntity> page = pipelines.findActivePage(ownerId, limit, offset);
        Map<UUID, Long> revisions = draftRevisions(page);
        List<PipelineResponse> items = page.stream()
                .map(pipeline -> response(pipeline, revisions.get(pipeline.getId())))
                .toList();
        return new PipelinePageResponse(items, pipelines.countActive(ownerId), limit, offset);
    }

    @Override
    @Transactional(readOnly = true)
    public PipelineResponse get(UUID pipelineId, UUID ownerId) {
        return response(writeGuard.requireReadable(pipelineId, ownerId));
    }

    @Override
    @Transactional
    public PipelineResponse rename(UUID pipelineId, UUID ownerId, String name) {
        PipelineEntity pipeline = writeGuard.lockWritableWithDraft(pipelineId, ownerId).pipeline();
        pipeline.rename(PipelineNames.require(name));
        PipelineEntity saved = pipelines.saveAndFlush(pipeline);
        return response(saved);
    }

    @Override
    @Transactional
    public void archive(UUID pipelineId, UUID ownerId) {
        PipelineEntity pipeline = writeGuard.lockOwnedWithDraft(pipelineId, ownerId).pipeline();
        pipeline.archive();
        pipelines.saveAndFlush(pipeline);
    }

    @Override
    @Transactional
    public PipelineResponse copy(UUID pipelineId, UUID ownerId, CopyPipelineRequest request) {
        PipelineWriteGuard.LockedPipeline locked = writeGuard.lockWritableWithDraft(pipelineId, ownerId);
        copyService.copy(locked.pipeline(), locked.draft(), ownerId, request);
        PipelineEntity copied = pipelines.findById(request.pipelineId())
                .orElseThrow(() -> notFound("流程不存在"));
        return response(copied, 0L);
    }

    private void requireCreateRequest(CreatePipelineRequest request) {
        if (request == null || request.ownerId() == null
                || request.pipelineId() == null || request.conversationId() == null) {
            throw validation("ownerId、pipelineId、conversationId 不能为空");
        }
    }

    private void requireSameBinding(PipelineEntity pipeline, UUID ownerId, UUID conversationId) {
        if (!Objects.equals(pipeline.getOwnerId(), ownerId)
                || !Objects.equals(pipeline.getConversationId(), conversationId)) {
            throw bindingConflict();
        }
    }

    private void ensureDraft(UUID pipelineId, UUID ownerId) {
        if (drafts.findById(pipelineId).isPresent()) {
            return;
        }
        drafts.insertIfAbsent(pipelineId, EMPTY_DRAFT, ownerId);
    }

    private PipelineResponse response(PipelineEntity pipeline) {
        return response(pipeline, drafts.findById(pipeline.getId())
                .map(PipelineDraftEntity::getRevision)
                .orElse(null));
    }

    private PipelineResponse response(PipelineEntity pipeline, Long revision) {
        return new PipelineResponse(
                pipeline.getId(),
                pipeline.getConversationId(),
                pipeline.getName(),
                pipeline.getStatus().value(),
                revision,
                pipeline.getCurrentVersionId(),
                pipeline.getCreatedAt(),
                pipeline.getUpdatedAt()
        );
    }

    private Map<UUID, Long> draftRevisions(List<PipelineEntity> pipelines) {
        if (pipelines.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> revisions = new LinkedHashMap<>();
        drafts.findAllById(pipelines.stream().map(PipelineEntity::getId).toList())
                .forEach(draft -> revisions.put(draft.getPipelineId(), draft.getRevision()));
        return revisions;
    }

}
