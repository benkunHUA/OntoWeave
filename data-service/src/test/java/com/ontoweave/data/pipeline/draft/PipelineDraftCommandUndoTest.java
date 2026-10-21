package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineEditOpEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineDraftCommandUndoTest extends PipelineDraftCommandServiceTestSupport {

    @Test
    void undoTurnAppendsRevisionAndRestoresBeforeSnapshot() {
        ObjectNode current = emptyDraft();
        lockDraft(6, current);
        PipelineEditOpEntity first = edit(5, "add_node", "node", NODE_ID, emptyDraft(), current);
        PipelineEditOpEntity second = edit(6, "update_node", "node", NODE_ID, current, current);
        when(editRecorder.findLatest(PIPELINE_ID)).thenReturn(Optional.of(second));
        when(editRecorder.findTurn(PIPELINE_ID, turnId(4))).thenReturn(List.of(first, second));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(6L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.undoTurn(PIPELINE_ID, OWNER_ID, turnId(4), 6L);

        assertThat(response.revision()).isEqualTo(7);
        assertThat(response.definition().path("nodes")).isEmpty();
        ArgumentCaptor<PipelineEditRecorder.SemanticEdit> edit =
                ArgumentCaptor.forClass(PipelineEditRecorder.SemanticEdit.class);
        verify(editRecorder).recordSemanticEdit(edit.capture());
        assertThat(edit.getValue().revision()).isEqualTo(7);
        assertThat(edit.getValue().opType()).isEqualTo("undo_turn");
        assertThat(edit.getValue().targetType()).isEqualTo("turn");
        assertThat(edit.getValue().targetId()).isEqualTo(turnId(4));
        verify(outbox).write(eq("pipeline"), eq(PIPELINE_ID), eq("pipeline.draft.changed"), any());
    }

    @Test
    void undoRejectsWhenTheLatestEditBelongsToAnotherTurn() {
        lockDraft(6, emptyDraft());
        PipelineEditOpEntity latest = new PipelineEditOpEntity(
                UUID.randomUUID(), PIPELINE_ID, 6, 1, "add_node", "node", NODE_ID,
                emptyDraft(), emptyDraft(), "user", OWNER_ID, operationId(6), UUID.randomUUID()
        );
        when(editRecorder.findLatest(PIPELINE_ID)).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.undoTurn(PIPELINE_ID, OWNER_ID, turnId(4), 6L))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(409);
                    assertThat(error.code()).isEqualTo("UNDO_NOT_LATEST");
                });
        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(outbox, never()).write(anyString(), any(), anyString(), any());
    }

    @Test
    void undoRestoresTheTurnStartButKeepsCurrentNodePositions() {
        ObjectNode turnStart = draftWithNode(position(10, 20));
        ObjectNode current = draftWithNode(position(100, 200));
        lockDraft(6, current);
        PipelineEditOpEntity first = edit(5, "add_node", "node", NODE_ID, turnStart, current);
        PipelineEditOpEntity second = edit(6, "update_node", "node", NODE_ID, current, current);
        when(editRecorder.findLatest(PIPELINE_ID)).thenReturn(Optional.of(second));
        when(editRecorder.findTurn(PIPELINE_ID, turnId(4))).thenReturn(List.of(first, second));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(6L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.undoTurn(PIPELINE_ID, OWNER_ID, turnId(4), 6L);

        JsonNode position = response.definition().path("nodes").get(0).path("position");
        assertThat(position.path("x").asInt()).isEqualTo(100);
        assertThat(position.path("y").asInt()).isEqualTo(200);
    }

    @Test
    void undoRejectsWhenTheTurnDoesNotEndAtCurrentRevision() {
        lockDraft(6, emptyDraft());
        PipelineEditOpEntity first = edit(4, "add_node", "node", NODE_ID, emptyDraft(), emptyDraft());
        PipelineEditOpEntity second = edit(5, "update_node", "node", NODE_ID, emptyDraft(), emptyDraft());
        when(editRecorder.findLatest(PIPELINE_ID)).thenReturn(Optional.of(second));
        when(editRecorder.findTurn(PIPELINE_ID, turnId(4))).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.undoTurn(PIPELINE_ID, OWNER_ID, turnId(4), 6L))
                .isInstanceOf(PipelineRevisionConflict.class);

        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(drafts, never()).updateDefinition(any(), eq(6L), anyString(), anyString(), any());
    }
}
