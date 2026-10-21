package com.ontoweave.data.pipeline.service;

import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineStatus;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

import static com.ontoweave.data.pipeline.service.PipelineErrors.archived;
import static com.ontoweave.data.pipeline.service.PipelineErrors.notFound;

@Component
public class PipelineWriteGuard {

    private final PipelineRepository pipelines;
    private final PipelineDraftRepository drafts;

    public PipelineWriteGuard(PipelineRepository pipelines, PipelineDraftRepository drafts) {
        this.pipelines = pipelines;
        this.drafts = drafts;
    }

    public PipelineEntity requireReadable(UUID pipelineId, UUID ownerId) {
        if (pipelineId == null || ownerId == null) {
            throw notFound("流程不存在");
        }
        return pipelines.findByIdAndOwnerId(pipelineId, ownerId)
                .orElseThrow(() -> notFound("流程不存在"));
    }

    public LockedPipeline lockWritableWithDraft(UUID pipelineId, UUID ownerId) {
        PipelineEntity pipeline = lockOwned(pipelineId, ownerId);
        if (pipeline.getStatus() == PipelineStatus.ARCHIVED) {
            throw archived();
        }
        return new LockedPipeline(pipeline, lockDraft(pipelineId));
    }

    public LockedPipeline lockOwnedWithDraft(UUID pipelineId, UUID ownerId) {
        return new LockedPipeline(lockOwned(pipelineId, ownerId), lockDraft(pipelineId));
    }

    private PipelineEntity lockOwned(UUID pipelineId, UUID ownerId) {
        if (pipelineId == null || ownerId == null) {
            throw notFound("流程不存在");
        }
        PipelineEntity pipeline = pipelines.lockById(pipelineId)
                .orElseThrow(() -> notFound("流程不存在"));
        if (!Objects.equals(pipeline.getOwnerId(), ownerId)) {
            throw notFound("流程不存在");
        }
        return pipeline;
    }

    private PipelineDraftEntity lockDraft(UUID pipelineId) {
        return drafts.findByIdForUpdate(pipelineId)
                .orElseThrow(() -> notFound("草稿不存在"));
    }

    public record LockedPipeline(PipelineEntity pipeline, PipelineDraftEntity draft) {
    }
}
