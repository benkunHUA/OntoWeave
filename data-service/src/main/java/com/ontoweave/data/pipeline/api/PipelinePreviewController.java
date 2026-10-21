package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.execution.preview.PipelinePreviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
public class PipelinePreviewController {

    private final PipelinePreviewService previews;

    public PipelinePreviewController(PipelinePreviewService previews) {
        this.previews = previews;
    }

    @PostMapping("/internal/v1/pipelines/{pipelineId}/previews")
    public PreviewResponse preview(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody PreviewRequest request
    ) {
        return previews.preview(pipelineId, ownerId, request);
    }

    @PostMapping("/internal/v1/pipelines/{pipelineId}/previews/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId
    ) {
        previews.cancel(pipelineId, ownerId);
        return ResponseEntity.noContent().build();
    }
}
