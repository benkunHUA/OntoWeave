package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.api.dto.SourceResponse;
import com.ontoweave.data.pipeline.source.PipelineSourceService;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
public class PipelineSourceController {

    private final PipelineSourceService service;

    public PipelineSourceController(PipelineSourceService service) {
        this.service = service;
    }

    @GetMapping("/internal/v1/pipelines/{pipelineId}/datasets/{datasetId}/sources")
    public SourceResponse get(
            @PathVariable UUID pipelineId,
            @PathVariable UUID datasetId,
            @RequestParam(required = false) UUID versionId,
            @RequestParam @NotNull UUID ownerId
    ) {
        return service.get(pipelineId, datasetId, versionId, ownerId);
    }
}
