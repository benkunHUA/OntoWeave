package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineDraftCommandServiceTest extends PipelineDraftCommandServiceTestSupport {

    @Test
    void semanticEditBumpsRevisionAndWritesEditAndEvent() {
        lockDraft(4, emptyDraft());
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(4L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.apply(PIPELINE_ID, OWNER_ID, addNode(4));

        assertThat(response.revision()).isEqualTo(5);
        assertThat(response.definition().path("nodes")).hasSize(1);
        assertThat(response.definition().path("nodes").get(0).path("id").asText())
                .isEqualTo(NODE_ID.toString());

        ArgumentCaptor<PipelineEditRecorder.SemanticEdit> edit =
                ArgumentCaptor.forClass(PipelineEditRecorder.SemanticEdit.class);
        verify(editRecorder).recordSemanticEdit(edit.capture());
        assertThat(edit.getValue().revision()).isEqualTo(5);
        assertThat(edit.getValue().opType()).isEqualTo("add_node");
        assertThat(edit.getValue().before().path("nodes")).isEmpty();
        assertThat(edit.getValue().after().path("nodes")).hasSize(1);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("pipeline"), eq(PIPELINE_ID), eq("pipeline.draft.changed"), payload.capture());
        JsonNode event = mapper.valueToTree(payload.getValue());
        assertThat(event.path("revision").asLong()).isEqualTo(5);
        assertThat(event.path("operationId").asText()).isEqualTo(operationId(4).toString());
        assertThat(event.path("turnId").asText()).isEqualTo(turnId(4).toString());
        assertThat(event.path("opType").asText()).isEqualTo("add_node");
    }

    @Test
    void rejectsStaleRevisionInsideTheLockBeforeAnyWrite() {
        lockDraft(5, emptyDraft());

        assertThatThrownBy(() -> service.apply(PIPELINE_ID, OWNER_ID, addNode(4)))
                .isInstanceOfSatisfying(PipelineRevisionConflict.class, conflict -> {
                    assertThat(conflict.status().value()).isEqualTo(409);
                    assertThat(conflict.code()).isEqualTo("REVISION_CONFLICT");
                });

        verify(drafts, never()).updateDefinition(any(), eq(4L), anyString(), anyString(), any());
        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(outbox, never()).write(anyString(), any(), anyString(), any());
    }

    @Test
    void conditionalUpdateStillProtectsAgainstRevisionConflict() {
        lockDraft(4, emptyDraft());
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(4L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.apply(PIPELINE_ID, OWNER_ID, addNode(4)))
                .isInstanceOf(PipelineRevisionConflict.class);

        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(outbox, never()).write(anyString(), any(), anyString(), any());
    }

    @Test
    void moveNodeUsesTheLockedRevisionAndWritesNoHistoryOrEvent() {
        lockDraft(5, draftWithNode(position(10, 20)));
        when(drafts.updateDefinitionWithoutRevision(eq(PIPELINE_ID), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.moveNode(
                PIPELINE_ID,
                OWNER_ID,
                new MoveNodeCommand(NODE_ID, position(100, 200))
        );

        assertThat(response.revision()).isEqualTo(5);
        assertThat(response.definition().path("nodes").get(0).path("position").path("x").asInt())
                .isEqualTo(100);
        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(outbox, never()).write(anyString(), any(), anyString(), any());
    }

    @Test
    void rejectsMalformedCommandWithConfigInvalid() {
        lockDraft(4, emptyDraft());
        AddNode command = new AddNode(
                4L, operationId(4), turnId(4), NODE_ID, "not-a-real-node", "Bad",
                mapper.createObjectNode(), position(1, 1)
        );

        assertConfigInvalid(() -> service.apply(PIPELINE_ID, OWNER_ID, command));
        verify(drafts, never()).updateDefinition(any(), eq(4L), anyString(), anyString(), any());
    }

    @Test
    void rejectsNonIntegralSchemaVersionForCommands() {
        assertInvalidSchemaVersion(mapper.getNodeFactory().textNode("1"));
        assertInvalidSchemaVersion(mapper.getNodeFactory().numberNode(1.5));
        assertInvalidSchemaVersion(mapper.getNodeFactory().booleanNode(true));

        verify(drafts, never()).updateDefinition(any(), eq(4L), anyString(), anyString(), any());
        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(outbox, never()).write(anyString(), any(), anyString(), any());
    }

    @Test
    void rejectsSemanticCommandWithoutTurnId() {
        AddNode command = new AddNode(
                4L, operationId(4), null, NODE_ID, "filter", "Filter",
                mapper.createObjectNode(), position(1, 1)
        );

        assertConfigInvalid(() -> service.apply(PIPELINE_ID, OWNER_ID, command));
        verify(drafts, never()).updateDefinition(any(), eq(4L), anyString(), anyString(), any());
        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(outbox, never()).write(anyString(), any(), anyString(), any());
    }

    @Test
    void updateNodeCanRenameAnOutputWithoutChangingItsBinding() {
        UUID outputId = UUID.randomUUID();
        lockDraft(5, outputDraft(outputId.toString()));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(5L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.apply(
                PIPELINE_ID,
                OWNER_ID,
                new UpdateNode(5L, operationId(5), turnId(5), NODE_ID, "Renamed output", null)
        );

        JsonNode node = response.definition().path("nodes").get(0);
        assertThat(node.path("name").asText()).isEqualTo("Renamed output");
        assertThat(node.path("config").path("outputId").asText()).isEqualTo(outputId.toString());
    }

    @Test
    void updateNodeRejectsChangingAnOutputBinding() {
        UUID outputId = UUID.randomUUID();
        lockDraft(5, outputDraft(outputId.toString()));
        ObjectNode config = mapper.createObjectNode();
        config.put("outputId", UUID.randomUUID().toString());

        assertConfigInvalid(() -> service.apply(
                PIPELINE_ID,
                OWNER_ID,
                new UpdateNode(5L, operationId(5), turnId(5), NODE_ID, null, config)
        ));
        verify(drafts, never()).updateDefinition(any(), eq(5L), anyString(), anyString(), any());
    }

    @Test
    void updateNodeRejectsClearingAnOutputBinding() {
        UUID outputId = UUID.randomUUID();
        lockDraft(5, outputDraft(outputId.toString()));

        assertConfigInvalid(() -> service.apply(
                PIPELINE_ID,
                OWNER_ID,
                new UpdateNode(
                        5L,
                        operationId(5),
                        turnId(5),
                        NODE_ID,
                        null,
                        mapper.createObjectNode()
                )
        ));
        verify(drafts, never()).updateDefinition(any(), eq(5L), anyString(), anyString(), any());
    }

    private void assertInvalidSchemaVersion(JsonNode schemaVersion) {
        PipelineDraftEntity draft = draft(4, draftWithSchemaVersion(schemaVersion));
        when(drafts.findByIdForUpdate(PIPELINE_ID)).thenReturn(java.util.Optional.of(draft));
        assertConfigInvalid(() -> service.apply(PIPELINE_ID, OWNER_ID, addNode(4)));
    }

    private void assertConfigInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.status().value()).isEqualTo(400);
            assertThat(error.code()).isEqualTo("CONFIG_INVALID");
        });
    }
}
