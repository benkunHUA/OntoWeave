package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEditOpEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.service.PipelineWriteGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.ontoweave.data.pipeline.draft.DraftConstants.CODE_NOT_FOUND;
import static com.ontoweave.data.pipeline.draft.DraftConstants.CODE_UNDO_NOT_LATEST;
import static com.ontoweave.data.pipeline.draft.DraftConstants.OP_SET_OUTPUT;
import static com.ontoweave.data.pipeline.draft.DraftConstants.OP_UNDO_TURN;
import static com.ontoweave.data.pipeline.draft.DraftConstants.TARGET_OUTPUT;
import static com.ontoweave.data.pipeline.draft.DraftConstants.TARGET_TURN;
import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.ACTOR_TYPE_USER;
import static com.ontoweave.data.pipeline.service.PipelineErrors.validation;

@Service
public class PipelineDraftCommandService {

    private final PipelineWriteGuard writeGuard;
    private final PipelineDraftRepository drafts;
    private final PipelineEditRecorder editRecorder;
    private final DraftCommandApplier applier;
    private final PipelineOutputWriter outputWriter;
    private final PipelineDraftEventWriter eventWriter;

    public PipelineDraftCommandService(
            PipelineWriteGuard writeGuard,
            PipelineDraftRepository drafts,
            PipelineOutputRepository outputs,
            PipelineDatasetRepository datasets,
            PipelineNodeCatalog catalog,
            ObjectMapper mapper,
            PipelineEditRecorder editRecorder,
            OutboxWriter outbox
    ) {
        this.writeGuard = writeGuard;
        this.drafts = drafts;
        this.editRecorder = editRecorder;
        this.applier = new DraftCommandApplier(catalog, mapper);
        this.outputWriter = new PipelineOutputWriter(outputs, datasets, mapper);
        this.eventWriter = new PipelineDraftEventWriter(mapper, outbox);
    }

    @Transactional
    public DraftResponse apply(UUID pipelineId, UUID ownerId, DraftCommand command) {
        PipelineWriteGuard.LockedPipeline locked = writeGuard.lockWritableWithDraft(pipelineId, ownerId);
        DraftCommandValidation.requireMetadata(command);
        long baseRevision = command.baseRevision();
        PipelineDraftEntity draft = locked.draft();
        if (draft.getRevision() != baseRevision) {
            throw new PipelineRevisionConflict();
        }
        JsonNode before = DraftCommandValidation.requireDefinition(draft);
        if (command instanceof SetOutput setOutput) {
            return applySetOutput(pipelineId, ownerId, baseRevision, before, setOutput);
        }
        DraftCommandApplier.DraftMutation mutation = applier.apply(before, command);
        return persistSemantic(
                pipelineId,
                ownerId,
                baseRevision,
                before,
                mutation,
                command.operationId(),
                command.turnId()
        );
    }

    @Transactional
    public DraftResponse moveNode(UUID pipelineId, UUID ownerId, MoveNodeCommand command) {
        PipelineWriteGuard.LockedPipeline locked = writeGuard.lockWritableWithDraft(pipelineId, ownerId);
        PipelineDraftEntity draft = locked.draft();
        JsonNode moved = applier.moveNode(DraftCommandValidation.requireDefinition(draft), command);
        int updated = drafts.updateDefinitionWithoutRevision(
                pipelineId,
                moved.toString(),
                ACTOR_TYPE_USER,
                ownerId
        );
        if (updated == 0) {
            throw notFound("草稿不存在");
        }
        return new DraftResponse(pipelineId, draft.getRevision(), moved);
    }

    @Transactional
    public DraftResponse undoTurn(UUID pipelineId, UUID ownerId, UUID turnId, Long currentRevision) {
        PipelineWriteGuard.LockedPipeline locked = writeGuard.lockWritableWithDraft(pipelineId, ownerId);
        if (currentRevision == null) {
            throw validation("currentRevision 不能为空");
        }
        PipelineDraftEntity draft = locked.draft();
        PipelineEditOpEntity latest = editRecorder.findLatest(pipelineId)
                .orElseThrow(this::undoNotLatest);
        if (turnId == null || latest.getTurnId() == null
                || !Objects.equals(latest.getTurnId(), turnId)) {
            throw undoNotLatest();
        }
        List<PipelineEditOpEntity> turnEdits = editRecorder.findTurn(pipelineId, turnId);
        if (turnEdits.isEmpty()) {
            throw undoNotLatest();
        }
        PipelineEditOpEntity last = turnEdits.get(turnEdits.size() - 1);
        if (draft.getRevision() != currentRevision || last.getRevision() != currentRevision) {
            throw new PipelineRevisionConflict();
        }
        JsonNode before = DraftCommandValidation.requireDefinition(draft);
        JsonNode restored = applier.preservePositions(turnEdits.get(0).getBefore(), before);
        UUID operationId = UUID.randomUUID();
        int updated = drafts.updateDefinition(
                pipelineId,
                currentRevision,
                restored.toString(),
                ACTOR_TYPE_USER,
                ownerId
        );
        if (updated == 0) {
            throw new PipelineRevisionConflict();
        }
        long revision = currentRevision + 1;
        editRecorder.recordSemanticEdit(new PipelineEditRecorder.SemanticEdit(
                pipelineId,
                revision,
                OP_UNDO_TURN,
                TARGET_TURN,
                turnId,
                before,
                restored,
                ACTOR_TYPE_USER,
                ownerId,
                operationId,
                turnId
        ));
        eventWriter.changed(pipelineId, revision, operationId, turnId, OP_UNDO_TURN);
        return new DraftResponse(pipelineId, revision, restored);
    }

    @Transactional(readOnly = true)
    public DraftResponse get(UUID pipelineId, UUID ownerId) {
        writeGuard.requireReadable(pipelineId, ownerId);
        PipelineDraftEntity draft = requireDraft(pipelineId);
        return new DraftResponse(
                pipelineId,
                draft.getRevision(),
                DraftCommandValidation.requireDefinition(draft)
        );
    }

    private DraftResponse applySetOutput(
            UUID pipelineId,
            UUID ownerId,
            long baseRevision,
            JsonNode before,
            SetOutput command
    ) {
        applier.validateOutputNode(before, command.nodeId());
        UUID outputId = outputWriter.write(
                pipelineId,
                command.nodeId(),
                command.name(),
                command.writeMode(),
                command.primaryKeyColumns(),
                command.expectations()
        );
        JsonNode after = applier.bindOutput(before, command.nodeId(), outputId).after();
        int updated = drafts.updateDefinition(
                pipelineId,
                baseRevision,
                after.toString(),
                ACTOR_TYPE_USER,
                ownerId
        );
        if (updated == 0) {
            throw new PipelineRevisionConflict();
        }
        long revision = baseRevision + 1;
        editRecorder.recordSemanticEdit(new PipelineEditRecorder.SemanticEdit(
                pipelineId,
                revision,
                OP_SET_OUTPUT,
                TARGET_OUTPUT,
                outputId,
                before,
                after,
                ACTOR_TYPE_USER,
                ownerId,
                command.operationId(),
                command.turnId()
        ));
        eventWriter.changed(pipelineId, revision, command.operationId(), command.turnId(), OP_SET_OUTPUT);
        return new DraftResponse(pipelineId, revision, after);
    }

    private DraftResponse persistSemantic(
            UUID pipelineId,
            UUID ownerId,
            long baseRevision,
            JsonNode before,
            DraftCommandApplier.DraftMutation mutation,
            UUID operationId,
            UUID turnId
    ) {
        int updated = drafts.updateDefinition(
                pipelineId,
                baseRevision,
                mutation.after().toString(),
                ACTOR_TYPE_USER,
                ownerId
        );
        if (updated == 0) {
            throw new PipelineRevisionConflict();
        }
        long revision = baseRevision + 1;
        editRecorder.recordSemanticEdit(new PipelineEditRecorder.SemanticEdit(
                pipelineId,
                revision,
                mutation.opType(),
                mutation.targetType(),
                mutation.targetId(),
                before,
                mutation.after(),
                ACTOR_TYPE_USER,
                ownerId,
                operationId,
                turnId
        ));
        eventWriter.changed(pipelineId, revision, operationId, turnId, mutation.opType());
        return new DraftResponse(pipelineId, revision, mutation.after());
    }

    private PipelineDraftEntity requireDraft(UUID pipelineId) {
        return drafts.findById(pipelineId)
                .orElseThrow(() -> notFound("草稿不存在"));
    }

    private ApiException undoNotLatest() {
        return new ApiException(
                HttpStatus.CONFLICT,
                CODE_UNDO_NOT_LATEST,
                "只能撤销最近一个编辑轮次"
        );
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_NOT_FOUND, message);
    }
}
