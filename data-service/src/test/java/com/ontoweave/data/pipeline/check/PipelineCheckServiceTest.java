package com.ontoweave.data.pipeline.check;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import com.ontoweave.data.pipeline.graph.InputSchema;
import com.ontoweave.data.pipeline.graph.InputSchemaResolver;
import com.ontoweave.data.pipeline.graph.PipelineGraphValidator;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineCheckServiceTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OUTPUT_NODE_ID = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesMultipleInputsWithOneBatchCall() {
        UUID datasetA = UUID.randomUUID();
        UUID datasetB = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(inputNode(UUID.randomUUID(), datasetA))
                        .add(inputNode(UUID.randomUUID(), datasetB)),
                mapper.createArrayNode()
        );
        InputSchemaResolver resolver = mock(InputSchemaResolver.class);
        when(resolver.resolveAll(eq(OWNER_ID), anySet())).thenReturn(Map.of());
        PipelineCheckService service = service(draft, List.of(), resolver);

        service.check(PIPELINE_ID, OWNER_ID, 3);

        verify(resolver).resolveAll(eq(OWNER_ID), eq(Set.of(datasetA, datasetB)));
        verify(resolver, never()).resolve(any(), any(), any());
    }

    @Test
    void reportsOutputMissingWhenActiveOutputHasNoDataset() {
        UUID datasetId = UUID.randomUUID();
        UUID outputId = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(inputNode(UUID.randomUUID(), datasetId))
                        .add(outputNode(outputId)),
                mapper.createArrayNode().add(edge(OUTPUT_NODE_ID))
        );
        PipelineOutputEntity output = new PipelineOutputEntity(
                outputId, PIPELINE_ID, OUTPUT_NODE_ID, PipelineOutputType.DATASET, "orders"
        );
        InputSchemaResolver resolver = mock(InputSchemaResolver.class);
        when(resolver.resolveAll(eq(OWNER_ID), anySet())).thenReturn(Map.of(
                datasetId,
                new InputSchema(UUID.randomUUID(), List.of(new com.ontoweave.data.pipeline.graph.FieldSchema(
                        "id", "long", false
                )))
        ));
        PipelineCheckService service = service(draft, List.of(output), resolver);

        PipelineCheckResult result = service.check(PIPELINE_ID, OWNER_ID, 3);

        assertThat(result.blockingIssues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("OUTPUT_MISSING");
                    assertThat(issue.nodeId()).isEqualTo(OUTPUT_NODE_ID.toString());
                    assertThat(issue.message()).contains("dataset");
                });
    }

    private PipelineCheckService service(
            ObjectNode definition,
            List<PipelineOutputEntity> outputs,
            InputSchemaResolver resolver
    ) {
        PipelineRepository pipelines = mock(PipelineRepository.class);
        PipelineDraftRepository drafts = mock(PipelineDraftRepository.class);
        PipelineOutputRepository outputRepository = mock(PipelineOutputRepository.class);
        PipelineDatasetRepository datasetRepository = mock(PipelineDatasetRepository.class);
        PipelineStageResolver stages = mock(PipelineStageResolver.class);
        PipelineEntity pipeline = new PipelineEntity(PIPELINE_ID, OWNER_ID, UUID.randomUUID(), "test");
        PipelineDraftEntity draft = mock(PipelineDraftEntity.class);
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID)).thenReturn(Optional.of(pipeline));
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(draft.getRevision()).thenReturn(3L);
        when(draft.getDefinition()).thenReturn(definition);
        when(outputRepository.findByPipelineIdAndArchivedAtIsNull(PIPELINE_ID)).thenReturn(outputs);
        when(datasetRepository.findByOutputIdIn(any())).thenReturn(List.of());
        when(stages.resolve(any(), eq(3L), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(PipelineStage.READY);
        return new PipelineCheckServiceImpl(
                pipelines,
                drafts,
                outputRepository,
                datasetRepository,
                new PipelineNodeCatalog(),
                new PipelineGraphValidator(),
                resolver,
                stages
        );
    }

    private ObjectNode draft(
            com.fasterxml.jackson.databind.node.ArrayNode nodes,
            com.fasterxml.jackson.databind.node.ArrayNode edges
    ) {
        ObjectNode draft = mapper.createObjectNode();
        draft.put("schemaVersion", 1);
        draft.set("nodes", nodes);
        draft.set("edges", edges);
        return draft;
    }

    private ObjectNode inputNode(UUID nodeId, UUID datasetId) {
        ObjectNode node = baseNode(nodeId, "input");
        ObjectNode config = node.putObject("config");
        config.put("datasetKind", "sync");
        config.put("datasetId", datasetId.toString());
        return node;
    }

    private ObjectNode outputNode(UUID outputId) {
        ObjectNode node = baseNode(OUTPUT_NODE_ID, "output");
        node.putObject("config").put("outputId", outputId.toString());
        return node;
    }

    private ObjectNode baseNode(UUID nodeId, String type) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", nodeId.toString());
        node.put("type", type);
        node.put("name", type);
        node.putObject("position").put("x", 1).put("y", 2);
        return node;
    }

    private ObjectNode edge(UUID targetNodeId) {
        ObjectNode edge = mapper.createObjectNode();
        edge.put("id", UUID.randomUUID().toString());
        edge.put("sourceNodeId", UUID.randomUUID().toString());
        edge.put("sourcePort", "out");
        edge.put("targetNodeId", targetNodeId.toString());
        edge.put("targetPort", "in");
        return edge;
    }
}
