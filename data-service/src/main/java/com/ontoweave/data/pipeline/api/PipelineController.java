package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.api.dto.CopyPipelineRequest;
import com.ontoweave.data.pipeline.api.dto.PipelinePageResponse;
import com.ontoweave.data.pipeline.api.dto.RenamePipelineRequest;
import com.ontoweave.data.pipeline.service.CreatePipelineRequest;
import com.ontoweave.data.pipeline.service.PipelineCreateResult;
import com.ontoweave.data.pipeline.service.PipelineResponse;
import com.ontoweave.data.pipeline.service.PipelineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/v1/pipelines")
public class PipelineController {

    private final PipelineService service;

    public PipelineController(PipelineService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PipelineResponse> create(
            @RequestHeader(value = "X-Operation-Id", required = false) UUID operationId,
            @Valid @RequestBody CreatePipelineRequest request
    ) {
        PipelineCreateResult result = service.create(request, operationId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.pipeline());
    }

    @GetMapping
    public PipelinePageResponse list(
            @RequestParam @NotNull UUID ownerId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        return service.list(ownerId, limit, offset);
    }

    @GetMapping("/{pipelineId}")
    public PipelineResponse get(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId
    ) {
        return service.get(pipelineId, ownerId);
    }

    @PatchMapping("/{pipelineId}")
    public PipelineResponse rename(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody RenamePipelineRequest request
    ) {
        return service.rename(pipelineId, ownerId, request.name());
    }

    @PostMapping("/{pipelineId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId
    ) {
        service.archive(pipelineId, ownerId);
    }

    @PostMapping("/{pipelineId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineResponse copy(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody CopyPipelineRequest request
    ) {
        return service.copy(pipelineId, ownerId, request);
    }
}
