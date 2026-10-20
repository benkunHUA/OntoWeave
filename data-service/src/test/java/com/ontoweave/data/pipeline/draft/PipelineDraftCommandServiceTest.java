package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetWriteMode;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEditOpEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PipelineDraftCommandServiceTest {

    private static final UUID PIPELINE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID NODE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID EDGE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private PipelineRepository pipelines;

    @Mock
    private PipelineDraftRepository drafts;

    @Mock
    private PipelineOutputRepository outputs;

    @Mock
    private PipelineDatasetRepository datasets;

    @Mock
    private PipelineEditRecorder editRecorder;

    @Mock
    private OutboxWriter outbox;

    private PipelineDraftCommandService service;

    @BeforeEach
    void setUp() {
        service = new PipelineDraftCommandService(
                pipelines,
                drafts,
                outputs,
                datasets,
                new PipelineNodeCatalog(),
                mapper,
                editRecorder,
                outbox
        );
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID)).thenReturn(Optional.of(
                new PipelineEntity(PIPELINE_ID, OWNER_ID, UUID.randomUUID(), "test")
        ));
    }

    @Test
    void semanticEditBumpsRevisionAndWritesEditAndEvent() {
        PipelineDraftEntity draft = draft(4, emptyDraft());
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
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
        assertThat(edit.getValue().targetType()).isEqualTo("node");
        assertThat(edit.getValue().targetId()).isEqualTo(NODE_ID);
        assertThat(edit.getValue().operationId()).isEqualTo(operationId(4));
        assertThat(edit.getValue().turnId()).isEqualTo(turnId(4));
        assertThat(edit.getValue().before().path("nodes")).isEmpty();
        assertThat(edit.getValue().after().path("nodes")).hasSize(1);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).write(eq("pipeline"), eq(PIPELINE_ID), eq("pipeline.draft.changed"), payload.capture());
        JsonNode event = mapper.valueToTree(payload.getValue());
        assertThat(event.path("pipelineId").asText()).isEqualTo(PIPELINE_ID.toString());
        assertThat(event.path("revision").asLong()).isEqualTo(5);
        assertThat(event.path("operationId").asText()).isEqualTo(operationId(4).toString());
        assertThat(event.path("turnId").asText()).isEqualTo(turnId(4).toString());
        assertThat(event.path("actorType").asText()).isEqualTo("user");
        assertThat(event.path("opType").asText()).isEqualTo("add_node");
    }

    @Test
    void rejectsAStaleRevision() {
        PipelineDraftEntity draft = draft(4, emptyDraft());
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(4L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.apply(PIPELINE_ID, OWNER_ID, addNode(4)))
                .isInstanceOfSatisfying(PipelineRevisionConflict.class, conflict -> {
                    assertThat(conflict.status().value()).isEqualTo(409);
                    assertThat(conflict.code()).isEqualTo("REVISION_CONFLICT");
                });

        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(outbox, never()).write(anyString(), any(), anyString(), any());
    }

    @Test
    void moveNodeKeepsRevisionAndWritesNoHistoryOrEvent() {
        ObjectNode definition = emptyDraft();
        ObjectNode node = definition.withArray("nodes").addObject();
        node.put("id", NODE_ID.toString());
        node.put("type", "filter");
        node.put("name", "Filter");
        node.set("config", mapper.createObjectNode());
        node.set("position", position(10, 20));
        PipelineDraftEntity draft = draft(5, definition);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
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
        PipelineDraftEntity draft = draft(4, emptyDraft());
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));

        AddNode command = new AddNode(
                4,
                operationId(4),
                turnId(4),
                NODE_ID,
                "not-a-real-node",
                "Bad",
                mapper.createObjectNode(),
                position(1, 1)
        );

        assertThatThrownBy(() -> service.apply(PIPELINE_ID, OWNER_ID, command))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(400);
                    assertThat(error.code()).isEqualTo("CONFIG_INVALID");
                });
        verify(drafts, never()).updateDefinition(any(), eq(4L), anyString(), anyString(), any());
    }

    @Test
    void setOutputUpdatesExistingRowsWithoutDuplicateInsert() {
        ObjectNode definition = outputDraft("55555555-5555-4555-8555-555555555555");
        PipelineDraftEntity draft = draft(5, definition);
        UUID outputId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        PipelineOutputEntity output = new PipelineOutputEntity(
                outputId, PIPELINE_ID, NODE_ID, PipelineOutputType.DATASET, "orders"
        );
        PipelineDatasetEntity dataset = new PipelineDatasetEntity(
                UUID.randomUUID(),
                outputId,
                PIPELINE_ID,
                "orders",
                mapper.createArrayNode(),
                mapper.createArrayNode()
        );
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(outputs.findByPipelineIdAndNodeId(PIPELINE_ID, NODE_ID)).thenReturn(Optional.of(output));
        when(outputs.findByPipelineIdAndName(PIPELINE_ID, "orders")).thenReturn(Optional.of(output));
        when(datasets.findByOutputId(outputId)).thenReturn(Optional.of(dataset));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(5L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.apply(PIPELINE_ID, OWNER_ID, setOutput(5, "orders", outputId));

        assertThat(response.revision()).isEqualTo(6);
        assertThat(response.definition().path("nodes").get(0).path("config").path("outputId").asText())
                .isEqualTo(outputId.toString());
        verify(outputs, never()).save(any());
        verify(datasets, never()).save(any());
    }

    @Test
    void setOutputRejectsDuplicateNameBeforeCreatingRows() {
        ObjectNode definition = outputDraft("55555555-5555-4555-8555-555555555555");
        PipelineDraftEntity draft = draft(5, definition);
        PipelineOutputEntity existing = new PipelineOutputEntity(
                UUID.randomUUID(), PIPELINE_ID, EDGE_ID, PipelineOutputType.DATASET, "orders"
        );
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(outputs.findByPipelineIdAndNodeId(PIPELINE_ID, NODE_ID)).thenReturn(Optional.empty());
        when(outputs.findByPipelineIdAndName(PIPELINE_ID, "orders")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.apply(
                PIPELINE_ID,
                OWNER_ID,
                setOutput(5, "orders", UUID.randomUUID())
        )).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.status().value()).isEqualTo(409);
            assertThat(error.code()).isEqualTo("OUTPUT_NAME_CONFLICT");
        });

        verify(outputs, never()).saveAndFlush(any());
        verify(datasets, never()).saveAndFlush(any());
        verify(drafts, never()).updateDefinition(any(), eq(5L), anyString(), anyString(), any());
    }

    @Test
    void setOutputUsesSnapshotAndEmptyArraysByDefault() {
        ObjectNode definition = outputDraft("");
        PipelineDraftEntity draft = draft(5, definition);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(5L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.apply(
                PIPELINE_ID,
                OWNER_ID,
                new SetOutput(5, operationId(5), turnId(5), NODE_ID, "orders", null, null, null)
        );

        ArgumentCaptor<PipelineDatasetEntity> dataset = ArgumentCaptor.forClass(PipelineDatasetEntity.class);
        verify(datasets).saveAndFlush(dataset.capture());
        assertThat(dataset.getValue().getName()).isEqualTo("orders");
        assertThat(dataset.getValue().getWriteMode()).isEqualTo(PipelineDatasetWriteMode.SNAPSHOT);
        assertThat(dataset.getValue().getPrimaryKeyColumns().isArray()).isTrue();
        assertThat(dataset.getValue().getExpectations().isArray()).isTrue();
        assertThat(dataset.getValue().getPrimaryKeyColumns()).isEmpty();
        assertThat(dataset.getValue().getExpectations()).isEmpty();
        assertThat(response.definition().path("nodes").get(0).path("config").path("outputId").asText())
                .isNotBlank();
    }

    @Test
    void undoTurnAppendsRevisionAndRestoresBeforeSnapshot() {
        ObjectNode current = emptyDraft();
        PipelineDraftEntity draft = draft(6, current);
        PipelineEditOpEntity first = edit(5, "add_node", "node", NODE_ID, emptyDraft(), current);
        PipelineEditOpEntity second = edit(6, "update_node", "node", NODE_ID, current, current);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(editRecorder.findLatest(PIPELINE_ID)).thenReturn(Optional.of(second));
        when(editRecorder.findTurn(PIPELINE_ID, turnId(4))).thenReturn(List.of(first, second));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(6L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.undoTurn(PIPELINE_ID, OWNER_ID, turnId(4), 6);

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
        PipelineDraftEntity draft = draft(6, emptyDraft());
        PipelineEditOpEntity latest = new PipelineEditOpEntity(
                UUID.randomUUID(),
                PIPELINE_ID,
                6,
                1,
                "add_node",
                "node",
                NODE_ID,
                emptyDraft(),
                emptyDraft(),
                "user",
                OWNER_ID,
                operationId(6),
                UUID.randomUUID()
        );
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(editRecorder.findLatest(PIPELINE_ID)).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.undoTurn(PIPELINE_ID, OWNER_ID, turnId(4), 6))
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
        PipelineDraftEntity draft = draft(6, current);
        PipelineEditOpEntity first = edit(5, "add_node", "node", NODE_ID, turnStart, current);
        PipelineEditOpEntity second = edit(6, "update_node", "node", NODE_ID, current, current);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(editRecorder.findLatest(PIPELINE_ID)).thenReturn(Optional.of(second));
        when(editRecorder.findTurn(PIPELINE_ID, turnId(4))).thenReturn(List.of(first, second));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(6L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.undoTurn(PIPELINE_ID, OWNER_ID, turnId(4), 6);

        JsonNode position = response.definition().path("nodes").get(0).path("position");
        assertThat(position.path("x").asInt()).isEqualTo(100);
        assertThat(position.path("y").asInt()).isEqualTo(200);
    }

    @Test
    void undoRejectsWhenTheTurnDoesNotEndAtCurrentRevision() {
        PipelineDraftEntity draft = draft(6, emptyDraft());
        PipelineEditOpEntity first = edit(4, "add_node", "node", NODE_ID, emptyDraft(), emptyDraft());
        PipelineEditOpEntity second = edit(5, "update_node", "node", NODE_ID, emptyDraft(), emptyDraft());
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(editRecorder.findLatest(PIPELINE_ID)).thenReturn(Optional.of(second));
        when(editRecorder.findTurn(PIPELINE_ID, turnId(4))).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.undoTurn(PIPELINE_ID, OWNER_ID, turnId(4), 6))
                .isInstanceOf(PipelineRevisionConflict.class);

        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(drafts, never()).updateDefinition(any(), eq(6L), anyString(), anyString(), any());
    }

    private PipelineDraftEntity draft(long revision, ObjectNode definition) {
        PipelineDraftEntity draft = org.mockito.Mockito.mock(PipelineDraftEntity.class);
        lenient().when(draft.getDefinition()).thenReturn(definition);
        lenient().when(draft.getRevision()).thenReturn(revision);
        return draft;
    }

    private PipelineEditOpEntity edit(
            long revision,
            String opType,
            String targetType,
            UUID targetId,
            JsonNode before,
            JsonNode after
    ) {
        return new PipelineEditOpEntity(
                UUID.randomUUID(),
                PIPELINE_ID,
                revision,
                1,
                opType,
                targetType,
                targetId,
                before,
                after,
                "user",
                OWNER_ID,
                operationId(4),
                turnId(4)
        );
    }

    private AddNode addNode(long baseRevision) {
        return new AddNode(
                baseRevision,
                operationId(baseRevision),
                turnId(baseRevision),
                NODE_ID,
                "filter",
                "Filter",
                mapper.createObjectNode(),
                position(1, 2)
        );
    }

    private SetOutput setOutput(long baseRevision, String name, UUID outputId) {
        return new SetOutput(
                baseRevision,
                operationId(baseRevision),
                turnId(baseRevision),
                NODE_ID,
                name,
                "snapshot",
                mapper.createArrayNode(),
                mapper.createArrayNode()
        );
    }

    private ObjectNode emptyDraft() {
        ObjectNode definition = mapper.createObjectNode();
        definition.put("schemaVersion", 1);
        definition.set("nodes", mapper.createArrayNode());
        definition.set("edges", mapper.createArrayNode());
        return definition;
    }

    private ObjectNode outputDraft(String outputId) {
        ObjectNode definition = emptyDraft();
        ObjectNode node = definition.withArray("nodes").addObject();
        node.put("id", NODE_ID.toString());
        node.put("type", "output");
        node.put("name", "Output");
        node.putObject("config").put("outputId", outputId);
        node.set("position", position(1, 2));
        return definition;
    }

    private ObjectNode draftWithNode(ObjectNode position) {
        ObjectNode definition = emptyDraft();
        ObjectNode node = definition.withArray("nodes").addObject();
        node.put("id", NODE_ID.toString());
        node.put("type", "filter");
        node.put("name", "Filter");
        node.set("config", mapper.createObjectNode());
        node.set("position", position);
        return definition;
    }

    private ObjectNode position(int x, int y) {
        ObjectNode position = mapper.createObjectNode();
        position.put("x", x);
        position.put("y", y);
        return position;
    }

    private UUID operationId(long revision) {
        return UUID.nameUUIDFromBytes(("operation-" + revision).getBytes());
    }

    private UUID turnId(long revision) {
        return UUID.nameUUIDFromBytes(("turn-" + revision).getBytes());
    }
}
