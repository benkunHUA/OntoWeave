package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineOutputLifecycleTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OUTPUT_NODE_ID = UUID.randomUUID();
    private static final UUID REMOVED_NODE_ID = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineOutputRepository outputs = mock(PipelineOutputRepository.class);
    private final PipelineDatasetRepository datasets = mock(PipelineDatasetRepository.class);
    private final PipelineOutputLifecycle lifecycle = new PipelineOutputLifecycle(outputs, datasets);

    @Test
    void archivesOutputAndDatasetWhenTheirNodeLeavesTheDraft() {
        PipelineOutputEntity output = output(REMOVED_NODE_ID);
        PipelineDatasetEntity dataset = dataset(output);
        when(outputs.findByPipelineIdAndArchivedAtIsNull(PIPELINE_ID)).thenReturn(List.of(output));
        when(datasets.findByOutputIdIn(List.of(output.getId()))).thenReturn(List.of(dataset));

        lifecycle.reconcile(PIPELINE_ID, draft(OUTPUT_NODE_ID));

        assertThat(output.isArchived()).isTrue();
        assertThat(output.getStatus()).isEqualTo("archived");
        assertThat(dataset.isArchived()).isTrue();
        verify(outputs).saveAll(List.of(output));
        verify(datasets).saveAll(List.of(dataset));
    }

    @Test
    void restoresOutputAndDatasetWhenUndoBringsTheirNodeBack() {
        PipelineOutputEntity output = output(OUTPUT_NODE_ID);
        PipelineDatasetEntity dataset = dataset(output);
        output.archive();
        dataset.archive();
        when(outputs.findByPipelineIdAndArchivedAtIsNull(PIPELINE_ID)).thenReturn(List.of());
        when(outputs.findByPipelineIdAndNodeIdIn(PIPELINE_ID, Set.of(OUTPUT_NODE_ID)))
                .thenReturn(List.of(output));
        when(datasets.findByOutputIdIn(List.of(output.getId()))).thenReturn(List.of(dataset));

        lifecycle.reconcile(PIPELINE_ID, draft(OUTPUT_NODE_ID));

        assertThat(output.isArchived()).isFalse();
        assertThat(output.getStatus()).isEqualTo("active");
        assertThat(dataset.isArchived()).isFalse();
        verify(outputs).saveAll(List.of(output));
        verify(datasets).saveAll(List.of(dataset));
    }

    private ObjectNode draft(UUID outputNodeId) {
        ObjectNode definition = mapper.createObjectNode();
        definition.put("schemaVersion", 1);
        ObjectNode node = definition.putArray("nodes").addObject();
        node.put("id", outputNodeId.toString());
        node.put("type", "output");
        node.put("name", "output");
        definition.putArray("edges");
        return definition;
    }

    private PipelineOutputEntity output(UUID nodeId) {
        return new PipelineOutputEntity(
                UUID.randomUUID(), PIPELINE_ID, nodeId,
                PipelineOutputType.DATASET, "orders"
        );
    }

    private PipelineDatasetEntity dataset(PipelineOutputEntity output) {
        return new PipelineDatasetEntity(
                UUID.randomUUID(), output.getId(), PIPELINE_ID, "orders",
                mapper.createArrayNode(), mapper.createArrayNode()
        );
    }

}
