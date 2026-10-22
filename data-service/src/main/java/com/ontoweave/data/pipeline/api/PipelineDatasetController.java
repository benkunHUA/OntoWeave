package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.source.PipelineDatasetPreviewService;
import com.ontoweave.data.sync.api.dto.DatasetPreviewResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
public class PipelineDatasetController {

    private final PipelineDatasetPreviewService previews;

    public PipelineDatasetController(PipelineDatasetPreviewService previews) {
        this.previews = previews;
    }

    @GetMapping("/internal/v1/pipelines/{pipelineId}/datasets/{datasetId}/preview")
    public DatasetPreviewResponse preview(
            @PathVariable UUID pipelineId,
            @PathVariable UUID datasetId,
            @RequestParam(required = false) UUID versionId,
            @RequestParam @NotNull UUID ownerId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return previews.preview(pipelineId, datasetId, versionId, ownerId, limit);
    }
}
