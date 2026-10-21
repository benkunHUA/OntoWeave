package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.api.dto.MoveNodeRequest;
import com.ontoweave.data.pipeline.api.dto.UndoRequest;
import com.ontoweave.data.pipeline.check.PipelineCheckResult;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.draft.DraftCommand;
import com.ontoweave.data.pipeline.draft.DraftResponse;
import com.ontoweave.data.pipeline.draft.MoveNodeCommand;
import com.ontoweave.data.pipeline.draft.PipelineDraftCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
public class PipelineDraftController {

    private final PipelineDraftCommandService draftCommands;
    private final PipelineCheckService checks;

    public PipelineDraftController(
            PipelineDraftCommandService draftCommands,
            PipelineCheckService checks
    ) {
        this.draftCommands = draftCommands;
        this.checks = checks;
    }

    @GetMapping("/internal/v1/pipelines/{pipelineId}/draft")
    public DraftResponse getDraft(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId
    ) {
        return draftCommands.get(pipelineId, ownerId);
    }

    @PostMapping("/internal/v1/pipelines/{pipelineId}/draft/commands")
    public DraftResponse command(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @RequestBody DraftCommand command
    ) {
        return draftCommands.apply(pipelineId, ownerId, command);
    }

    @PostMapping("/internal/v1/pipelines/{pipelineId}/draft/positions")
    public DraftResponse move(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody MoveNodeRequest request
    ) {
        return draftCommands.moveNode(
                pipelineId,
                ownerId,
                new MoveNodeCommand(request.nodeId(), request.position())
        );
    }

    @GetMapping("/internal/v1/pipelines/{pipelineId}/check")
    public PipelineCheckResult check(
            @PathVariable UUID pipelineId,
            @RequestParam @NotNull UUID ownerId,
            @RequestParam @Min(0) long revision
    ) {
        return checks.check(pipelineId, ownerId, revision);
    }

    @PostMapping("/internal/v1/pipelines/{pipelineId}/turns/{turnId}/undo")
    public DraftResponse undo(
            @PathVariable UUID pipelineId,
            @PathVariable UUID turnId,
            @RequestParam @NotNull UUID ownerId,
            @Valid @RequestBody UndoRequest request
    ) {
        return draftCommands.undoTurn(pipelineId, ownerId, turnId, request.currentRevision());
    }
}
