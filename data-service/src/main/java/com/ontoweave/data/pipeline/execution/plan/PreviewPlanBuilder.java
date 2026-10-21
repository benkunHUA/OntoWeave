package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
import com.ontoweave.data.pipeline.execution.build.PipelineInputResolver;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class PreviewPlanBuilder {

    private final PipelineRepository pipelines;
    private final PipelineDraftRepository drafts;
    private final PipelineInputResolver inputResolver;

    public PreviewPlanBuilder(
            PipelineRepository pipelines,
            PipelineDraftRepository drafts,
            PipelineInputResolver inputResolver
    ) {
        this.pipelines = pipelines;
        this.drafts = drafts;
        this.inputResolver = inputResolver;
    }

    public LogicalPlan buildForPreview(UUID pipelineId, UUID ownerId, long revision, String nodeId) {
        pipelines.findByIdAndOwnerId(pipelineId, ownerId)
                .orElseThrow(() -> notFound("流程不存在"));
        PipelineDraftEntity draft = drafts.findById(pipelineId)
                .orElseThrow(() -> notFound("流程草稿不存在"));
        if (draft.getRevision() != revision) {
            throw new PipelineRevisionConflict();
        }
        return plan(
                draft.getDefinition(),
                revision,
                nodeId,
                inputResolver.resolve(pipelineId, ownerId, revision),
                ScanLimit.preview(PipelineExecutionLimits.DEFAULT_SCAN_LIMIT)
        );
    }

    public LogicalPlan buildForBuild(
            PipelineVersionEntity version,
            List<ResolvedInput> resolvedInputs,
            String nodeId
    ) {
        if (version == null) {
            throw notFound("流程版本不存在");
        }
        return plan(
                version.getDefinition(),
                version.getSourceDraftRevision(),
                nodeId,
                resolvedInputs,
                ScanLimit.unlimited()
        );
    }

    public LogicalPlan build(UUID pipelineId, UUID ownerId, long revision, String nodeId) {
        return buildForPreview(pipelineId, ownerId, revision, nodeId);
    }

    private LogicalPlan plan(
            JsonNode definition,
            long revision,
            String nodeId,
            List<ResolvedInput> resolvedInputs,
            ScanLimit scanLimit
    ) {
        return new LogicalPlanAssembler(definition, revision, nodeId, resolvedInputs, scanLimit)
                .assemble();
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
