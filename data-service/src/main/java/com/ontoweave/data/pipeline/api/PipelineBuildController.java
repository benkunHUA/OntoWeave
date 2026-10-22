package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.execution.build.BuildResponse;
import com.ontoweave.data.pipeline.execution.build.CreateBuildRequest;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
public class PipelineBuildController {

    private final PipelineBuildService builds;

    public PipelineBuildController(PipelineBuildService builds) {
        this.builds = builds;
    }

    @PostMapping("/internal/v1/pipelines/{pipelineId}/builds")
    public BuildResponse enqueue(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody CreateBuildRequest request
    ) {
        return builds.enqueue(pipelineId, ownerId, request);
    }

    @GetMapping("/internal/v1/pipelines/{pipelineId}/builds/{buildId}")
    public BuildResponse get(
            @PathVariable UUID pipelineId,
            @PathVariable UUID buildId,
            @RequestParam @NotNull UUID ownerId
    ) {
        return builds.get(pipelineId, buildId, ownerId);
    }

    @GetMapping("/internal/v1/pipelines/{pipelineId}/builds/latest")
    public BuildResponse latest(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId
    ) {
        return builds.latest(pipelineId, ownerId);
    }

    @PostMapping("/internal/v1/pipelines/{pipelineId}/builds/{buildId}/cancel")
    public void cancel(
            @PathVariable UUID pipelineId,
            @PathVariable UUID buildId,
            @RequestParam @NotNull UUID ownerId
    ) {
        builds.requestCancel(pipelineId, buildId, ownerId);
    }
}
