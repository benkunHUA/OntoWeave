package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PipelineVersionFreezerTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final long REVISION = 7L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final PipelineDraftRepository drafts = mock(PipelineDraftRepository.class);
    private final PipelineOutputRepository outputs = mock(PipelineOutputRepository.class);
    private final PipelineDatasetRepository datasets = mock(PipelineDatasetRepository.class);
    private final PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
    private final PipelineDefinitionHasher hasher = mock(PipelineDefinitionHasher.class);
    private final PipelineInputResolver inputs = mock(PipelineInputResolver.class);
    private final PipelineVersionFreezer freezer = new PipelineVersionFreezerImpl(
            pipelines, drafts, outputs, datasets, versions, hasher, inputs, mapper
    );

    @Test
    void reusesVersionForSameDefinitionHashAndResolvesInputsAgain() {
        PipelineEntity pipeline = pipeline();
        PipelineDraftEntity draft = draft();
        PipelineOutputEntity output = output();
        PipelineDatasetEntity dataset = dataset(output);
        PipelineVersionEntity existing = version(3, "sha-256");
        ResolvedInput input = resolvedInput();
        when(pipelines.lockActiveByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(pipeline));
        when(drafts.findByIdForUpdate(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(outputs.findByPipelineIdAndArchivedAtIsNull(PIPELINE_ID)).thenReturn(List.of(output));
        when(datasets.findByOutputIdIn(anyCollection())).thenReturn(List.of(dataset));
        when(hasher.normalizedHash(any(), any(), any())).thenReturn("sha-256");
        when(versions.findByPipelineIdAndDefinitionHash(PIPELINE_ID, "sha-256"))
                .thenReturn(Optional.of(existing));
        when(inputs.resolve(existing)).thenReturn(List.of(input));

        FrozenBuild frozen = freezer.freeze(PIPELINE_ID, OWNER_ID, REVISION);

        assertThat(frozen.version()).isSameAs(existing);
        assertThat(frozen.resolvedInputs()).containsExactly(input);
        verify(versions, never()).saveAndFlush(any());
    }

    @Test
    void allocatesNextVersionAndFreezesOutputsAndInputRefs() {
        PipelineEntity pipeline = pipeline();
        PipelineDraftEntity draft = draft();
        PipelineOutputEntity output = output();
        PipelineDatasetEntity dataset = dataset(output);
        PipelineVersionEntity latest = version(3, "old");
        ResolvedInput input = resolvedInput();
        when(pipelines.lockActiveByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(pipeline));
        when(drafts.findByIdForUpdate(PIPELINE_ID)).thenReturn(Optional.of(draft));
        when(outputs.findByPipelineIdAndArchivedAtIsNull(PIPELINE_ID)).thenReturn(List.of(output));
        when(datasets.findByOutputIdIn(anyCollection())).thenReturn(List.of(dataset));
        when(hasher.normalizedHash(any(), any(), any())).thenReturn("new-hash");
        when(versions.findByPipelineIdAndDefinitionHash(PIPELINE_ID, "new-hash"))
                .thenReturn(Optional.empty());
        when(versions.findTopByPipelineIdOrderByVersionNumberDesc(PIPELINE_ID))
                .thenReturn(Optional.of(latest));
        when(versions.saveAndFlush(any(PipelineVersionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inputs.resolve(any(PipelineVersionEntity.class))).thenReturn(List.of(input));

        FrozenBuild frozen = freezer.freeze(PIPELINE_ID, OWNER_ID, REVISION);

        assertThat(frozen.version().getVersionNumber()).isEqualTo(4);
        assertThat(frozen.version().getDefinitionHash()).isEqualTo("new-hash");
        assertThat(frozen.version().getOutputs()).hasSize(1);
        assertThat(frozen.version().getInputRefs()).hasSize(1);
        assertThat(frozen.resolvedInputs()).containsExactly(input);
    }

    @Test
    void archivedPipelineCannotBeFrozen() {
        PipelineEntity archived = pipeline();
        when(pipelines.lockActiveByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.empty());
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> freezer.freeze(PIPELINE_ID, OWNER_ID, REVISION))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("PIPELINE_ARCHIVED"));
        verifyNoInteractions(drafts, versions);
    }

    private PipelineEntity pipeline() {
        PipelineEntity pipeline = mock(PipelineEntity.class);
        when(pipeline.getId()).thenReturn(PIPELINE_ID);
        when(pipeline.getOwnerId()).thenReturn(OWNER_ID);
        return pipeline;
    }

    private PipelineDraftEntity draft() {
        ObjectNode definition = mapper.createObjectNode();
        definition.put("schemaVersion", 1);
        ObjectNode input = definition.putArray("nodes").addObject();
        input.put("id", UUID.randomUUID().toString());
        input.put("type", "input");
        input.putObject("config").put("datasetKind", "sync").put("datasetId", UUID.randomUUID().toString());
        ObjectNode outputNode = definition.withArray("nodes").addObject();
        outputNode.put("id", UUID.randomUUID().toString());
        outputNode.put("type", "output");
        definition.putArray("edges");
        PipelineDraftEntity draft = mock(PipelineDraftEntity.class);
        when(draft.getRevision()).thenReturn(REVISION);
        when(draft.getDefinition()).thenReturn(definition);
        return draft;
    }

    private PipelineOutputEntity output() {
        return new PipelineOutputEntity(
                UUID.randomUUID(), PIPELINE_ID, UUID.randomUUID(),
                PipelineOutputType.DATASET, "orders"
        );
    }

    private PipelineDatasetEntity dataset(PipelineOutputEntity output) {
        return new PipelineDatasetEntity(
                UUID.randomUUID(), output.getId(), PIPELINE_ID, "orders",
                mapper.createArrayNode(), mapper.createArrayNode()
        );
    }

    private PipelineVersionEntity version(int number, String hash) {
        return new PipelineVersionEntity(
                UUID.randomUUID(), PIPELINE_ID, number, mapper.createObjectNode(),
                mapper.createArrayNode(), hash, mapper.createArrayNode(), REVISION, OWNER_ID
        );
    }

    private ResolvedInput resolvedInput() {
        return new ResolvedInput(
                "sync", UUID.randomUUID(), UUID.randomUUID(), 2, "schema",
                "ontoweave", "sales", "orders", 10L, null
        );
    }
}
