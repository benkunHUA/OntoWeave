package com.ontoweave.data.pipeline.check;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PipelineStageResolver {

    private final PipelineBuildRepository builds;
    private final PipelineVersionRepository versions;

    public PipelineStageResolver(
            PipelineBuildRepository builds,
            PipelineVersionRepository versions
    ) {
        this.builds = builds;
        this.versions = versions;
    }

    public PipelineStage resolve(
            PipelineEntity pipeline,
            long revision,
            boolean empty,
            boolean hasInput,
            boolean hasOutput,
            boolean hasBlockingIssues
    ) {
        if (builds.existsByPipelineIdAndStatusIn(
                pipeline.getId(),
                List.of(PipelineBuildStatus.QUEUED, PipelineBuildStatus.RUNNING)
        )) {
            return PipelineStage.BUILDING;
        }
        if (empty) {
            return PipelineStage.EMPTY;
        }
        if (!hasInput) {
            return PipelineStage.NEEDS_INPUT;
        }
        if (!hasOutput) {
            return PipelineStage.NEEDS_OUTPUT;
        }
        if (hasBlockingIssues) {
            return PipelineStage.INVALID;
        }
        PipelineBuildEntity latest = builds.findTopByPipelineIdOrderByCreatedAtDesc(pipeline.getId())
                .orElse(null);
        if (latest != null && latest.getStatus() == PipelineBuildStatus.FAILED
                && sourceRevision(latest.getPipelineVersionId()) == revision) {
            return PipelineStage.FAILED;
        }
        PipelineVersionEntity current = currentVersion(pipeline);
        if (current != null) {
            if (current.getSourceDraftRevision() < revision) {
                return PipelineStage.STALE;
            }
            if (current.getSourceDraftRevision() == revision) {
                return PipelineStage.SUCCEEDED;
            }
        }
        return PipelineStage.READY;
    }

    private PipelineVersionEntity currentVersion(PipelineEntity pipeline) {
        if (pipeline.getCurrentVersionId() == null) {
            return null;
        }
        return versions.findById(pipeline.getCurrentVersionId()).orElse(null);
    }

    private long sourceRevision(java.util.UUID versionId) {
        return versions.findById(versionId)
                .map(PipelineVersionEntity::getSourceDraftRevision)
                .orElse(Long.MIN_VALUE);
    }
}
