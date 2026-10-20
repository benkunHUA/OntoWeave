package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEditOpEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
abstract class PipelineDraftCommandServiceTestSupport {

    protected static final UUID PIPELINE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    protected static final UUID OWNER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    protected static final UUID NODE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    protected static final UUID EDGE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    protected final ObjectMapper mapper = new ObjectMapper();

    @Mock
    protected PipelineRepository pipelines;

    @Mock
    protected PipelineDraftRepository drafts;

    @Mock
    protected PipelineOutputRepository outputs;

    @Mock
    protected PipelineDatasetRepository datasets;

    @Mock
    protected PipelineEditRecorder editRecorder;

    @Mock
    protected OutboxWriter outbox;

    protected PipelineDraftCommandService service;

    @BeforeEach
    void setUpService() {
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
        lenient().when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID)).thenReturn(Optional.of(
                new PipelineEntity(PIPELINE_ID, OWNER_ID, UUID.randomUUID(), "test")
        ));
    }

    protected PipelineDraftEntity lockDraft(long revision, ObjectNode definition) {
        PipelineDraftEntity draft = draft(revision, definition);
        when(drafts.findByIdForUpdate(PIPELINE_ID)).thenReturn(Optional.of(draft));
        return draft;
    }

    protected PipelineDraftEntity draft(long revision, ObjectNode definition) {
        PipelineDraftEntity draft = org.mockito.Mockito.mock(PipelineDraftEntity.class);
        lenient().when(draft.getDefinition()).thenReturn(definition);
        lenient().when(draft.getRevision()).thenReturn(revision);
        return draft;
    }

    protected PipelineEditOpEntity edit(
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

    protected AddNode addNode(long baseRevision) {
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

    protected SetOutput setOutput(long baseRevision, String name) {
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

    protected ObjectNode emptyDraft() {
        ObjectNode definition = mapper.createObjectNode();
        definition.put("schemaVersion", 1);
        definition.set("nodes", mapper.createArrayNode());
        definition.set("edges", mapper.createArrayNode());
        return definition;
    }

    protected ObjectNode draftWithSchemaVersion(JsonNode schemaVersion) {
        ObjectNode definition = emptyDraft();
        definition.set("schemaVersion", schemaVersion);
        return definition;
    }

    protected ObjectNode outputDraft(String outputId) {
        ObjectNode definition = emptyDraft();
        ObjectNode node = definition.withArray("nodes").addObject();
        node.put("id", NODE_ID.toString());
        node.put("type", "output");
        node.put("name", "Output");
        node.putObject("config").put("outputId", outputId);
        node.set("position", position(1, 2));
        return definition;
    }

    protected ObjectNode draftWithNode(ObjectNode position) {
        ObjectNode definition = emptyDraft();
        ObjectNode node = definition.withArray("nodes").addObject();
        node.put("id", NODE_ID.toString());
        node.put("type", "filter");
        node.put("name", "Filter");
        node.set("config", mapper.createObjectNode());
        node.set("position", position);
        return definition;
    }

    protected ObjectNode position(int x, int y) {
        ObjectNode position = mapper.createObjectNode();
        position.put("x", x);
        position.put("y", y);
        return position;
    }

    protected UUID operationId(long revision) {
        return UUID.nameUUIDFromBytes(("operation-" + revision).getBytes());
    }

    protected UUID turnId(long revision) {
        return UUID.nameUUIDFromBytes(("turn-" + revision).getBytes());
    }
}
