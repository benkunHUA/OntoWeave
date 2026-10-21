package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.draft.AddNode;
import com.ontoweave.data.pipeline.draft.DraftResponse;
import com.ontoweave.data.pipeline.draft.MoveNodeCommand;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
import com.ontoweave.data.pipeline.draft.SetOutput;
import com.ontoweave.data.pipeline.draft.UpdateNode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineDraftCommandIntegrationTest extends PipelineDraftIntegrationTestSupport {

    @Test
    @Transactional
    void conditionalUpdateOnlySucceedsForTheExpectedRevision() {
        insertDraft(emptyDraft(), 4);
        String next = emptyDraft().toString();

        assertThat(drafts.updateDefinition(pipelineId, 4, next, "user", ownerId)).isEqualTo(1);
        assertThat(drafts.updateDefinition(pipelineId, 4, next, "user", ownerId)).isZero();
        assertThat(revision()).isEqualTo(5);
    }

    @Test
    void revisionConflictDoesNotAppendEditOrOutboxEvent() {
        insertDraft(emptyDraft(), 5);

        assertThatThrownBy(() -> service.apply(pipelineId, ownerId, addNode(4, "Filter")))
                .isInstanceOf(PipelineRevisionConflict.class);

        assertThat(revision()).isEqualTo(5);
        assertThat(count("data.pipeline_edit_op")).isZero();
        assertThat(count("data.outbox_event")).isZero();
    }

    @Test
    void semanticEditPersistsDraftEditAndEventTogether() {
        insertDraft(emptyDraft(), 4);

        DraftResponse response = service.apply(pipelineId, ownerId, addNode(4, "Filter"));

        assertThat(response.revision()).isEqualTo(5);
        assertThat(revision()).isEqualTo(5);
        assertThat(count("data.pipeline_edit_op")).isEqualTo(1);
        assertThat(count("data.outbox_event")).isEqualTo(1);

        Map<String, Object> edit = jdbc.queryForMap("""
                SELECT revision, op_type, target_type, target_id, operation_id, turn_id
                FROM data.pipeline_edit_op
                """);
        assertThat(edit.get("revision")).isEqualTo(5L);
        assertThat(edit.get("op_type")).isEqualTo("add_node");
        assertThat(edit.get("target_type")).isEqualTo("node");

        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM data.outbox_event",
                String.class
        );
        assertThat(payload).contains("\"revision\": 5", "\"opType\": \"add_node\"");
    }

    @Test
    void moveNodeKeepsRevisionAndWritesNoHistory() {
        insertDraft(emptyDraft(), 4);
        DraftResponse added = service.apply(pipelineId, ownerId, addNode(4, "Filter"));
        UUID nodeId = added.definition().path("nodes").get(0).path("id").toString()
                .replace("\"", "")
                .transform(UUID::fromString);

        DraftResponse moved = service.moveNode(
                pipelineId,
                ownerId,
                new MoveNodeCommand(nodeId, mapper.valueToTree(Map.of("x", 300, "y", 400)))
        );

        assertThat(moved.revision()).isEqualTo(5);
        assertThat(revision()).isEqualTo(5);
        assertThat(count("data.pipeline_edit_op")).isEqualTo(1);
        assertThat(count("data.outbox_event")).isEqualTo(1);
        assertThat(moved.definition().path("nodes").get(0).path("position").path("x").asInt())
                .isEqualTo(300);
    }

    @Test
    void setOutputDoesNotDuplicateOutputRows() {
        UUID outputNodeId = UUID.randomUUID();
        insertDraft(outputDraft(outputNodeId), 4);

        DraftResponse first = service.apply(
                pipelineId,
                ownerId,
                new SetOutput(4L, operationId(4), turnId(4), outputNodeId, "orders", "snapshot", null, null)
        );
        DraftResponse second = service.apply(
                pipelineId,
                ownerId,
                new SetOutput(5L, operationId(5), turnId(5), outputNodeId, "orders_clean", "snapshot", null, null)
        );

        assertThat(first.revision()).isEqualTo(5);
        assertThat(second.revision()).isEqualTo(6);
        assertThat(count("data.pipeline_output")).isEqualTo(1);
        assertThat(count("data.pipeline_dataset")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM data.pipeline_dataset", String.class))
                .isEqualTo("orders_clean");
        assertThat(second.definition().path("nodes").get(0).path("config").path("outputId").asText())
                .isEqualTo(jdbc.queryForObject("SELECT id::text FROM data.pipeline_output", String.class));
        assertThat(second.definition().path("nodes").get(0).path("config").size()).isEqualTo(1);
    }

    @Test
    void setOutputRollsBackRowsWhenRevisionConflicts() {
        UUID outputNodeId = UUID.randomUUID();
        insertDraft(outputDraft(outputNodeId), 5);

        assertThatThrownBy(() -> service.apply(
                pipelineId,
                ownerId,
                new SetOutput(4L, operationId(4), turnId(4), outputNodeId, "orders", "snapshot", null, null)
        )).isInstanceOf(PipelineRevisionConflict.class);

        assertThat(revision()).isEqualTo(5);
        assertThat(count("data.pipeline_output")).isZero();
        assertThat(count("data.pipeline_dataset")).isZero();
        assertThat(count("data.pipeline_edit_op")).isZero();
        assertThat(count("data.outbox_event")).isZero();
    }

    @Test
    void undoTurnAppendsRevisionAndRestoresTheTurnStart() {
        insertDraft(emptyDraft(), 4);
        DraftResponse added = service.apply(pipelineId, ownerId, addNode(4, "Filter"));
        UUID nodeId = UUID.fromString(added.definition().path("nodes").get(0).path("id").asText());
        service.apply(
                pipelineId,
                ownerId,
                new UpdateNode(5L, operationId(5), turnId(4), nodeId, "Renamed", null)
        );

        DraftResponse undone = service.undoTurn(pipelineId, ownerId, turnId(4), 6L);

        assertThat(undone.revision()).isEqualTo(7);
        assertThat(undone.definition().path("nodes")).isEmpty();
        assertThat(revision()).isEqualTo(7);
        assertThat(count("data.pipeline_edit_op")).isEqualTo(3);
        Map<String, Object> undo = jdbc.queryForMap("""
                SELECT revision, op_type, target_type, target_id, turn_id
                FROM data.pipeline_edit_op
                ORDER BY revision DESC LIMIT 1
                """);
        assertThat(undo.get("revision")).isEqualTo(7L);
        assertThat(undo.get("op_type")).isEqualTo("undo_turn");
        assertThat(undo.get("target_type")).isEqualTo("turn");
        assertThat(undo.get("target_id")).isEqualTo(turnId(4));
        assertThat(undo.get("turn_id")).isEqualTo(turnId(4));
    }

    private AddNode addNode(long baseRevision, String name) {
        return new AddNode(
                baseRevision,
                operationId(baseRevision),
                turnId(4),
                UUID.randomUUID(),
                "filter",
                name,
                mapper.createObjectNode(),
                mapper.valueToTree(Map.of("x", 10, "y", 20))
        );
    }

    private ObjectNode outputDraft(UUID outputNodeId) {
        ObjectNode definition = emptyDraft();
        ObjectNode node = definition.withArray("nodes").addObject();
        node.put("id", outputNodeId.toString());
        node.put("type", "output");
        node.put("name", "Output");
        node.putObject("config");
        node.set("position", mapper.valueToTree(Map.of("x", 10, "y", 20)));
        return definition;
    }

}
