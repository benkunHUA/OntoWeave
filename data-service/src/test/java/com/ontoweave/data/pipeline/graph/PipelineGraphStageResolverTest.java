package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.check.PipelineStage;
import com.ontoweave.data.pipeline.check.PipelineStageResolver;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineGraphStageResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesStagePriorityInContractOrder() {
        PipelineEntity pipeline = mock(PipelineEntity.class);
        PipelineBuildRepository builds = mock(PipelineBuildRepository.class);
        PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
        PipelineStageResolver resolver = new PipelineStageResolver(builds, versions);
        UUID pipelineId = UUID.randomUUID();
        when(pipeline.getId()).thenReturn(pipelineId);
        when(builds.existsByPipelineIdAndStatusIn(eq(pipelineId), anyList())).thenReturn(true);

        assertThat(resolver.resolve(pipeline, 5, true, false, false, true))
                .isEqualTo(PipelineStage.BUILDING);

        when(builds.existsByPipelineIdAndStatusIn(eq(pipelineId), anyList())).thenReturn(false);
        assertThat(resolver.resolve(pipeline, 5, true, false, false, true))
                .isEqualTo(PipelineStage.EMPTY);
        assertThat(resolver.resolve(pipeline, 5, false, false, true, true))
                .isEqualTo(PipelineStage.NEEDS_INPUT);
        assertThat(resolver.resolve(pipeline, 5, false, true, false, true))
                .isEqualTo(PipelineStage.NEEDS_OUTPUT);
        assertThat(resolver.resolve(pipeline, 5, false, true, true, true))
                .isEqualTo(PipelineStage.INVALID);

        UUID failedVersionId = UUID.randomUUID();
        PipelineBuildEntity failed = mock(PipelineBuildEntity.class);
        PipelineVersionEntity failedVersion = mock(PipelineVersionEntity.class);
        when(failed.getStatus()).thenReturn(PipelineBuildStatus.FAILED);
        when(failed.getPipelineVersionId()).thenReturn(failedVersionId);
        when(failedVersion.getSourceDraftRevision()).thenReturn(5L);
        when(builds.findTopByPipelineIdOrderByCreatedAtDesc(pipelineId)).thenReturn(Optional.of(failed));
        when(versions.findById(failedVersionId)).thenReturn(Optional.of(failedVersion));
        assertThat(resolver.resolve(pipeline, 5, false, true, true, false))
                .isEqualTo(PipelineStage.FAILED);

        UUID currentVersionId = UUID.randomUUID();
        PipelineVersionEntity current = mock(PipelineVersionEntity.class);
        when(pipeline.getCurrentVersionId()).thenReturn(currentVersionId);
        when(versions.findById(currentVersionId)).thenReturn(Optional.of(current));
        when(builds.findTopByPipelineIdOrderByCreatedAtDesc(pipelineId)).thenReturn(Optional.empty());
        when(current.getSourceDraftRevision()).thenReturn(4L);
        assertThat(resolver.resolve(pipeline, 5, false, true, true, false))
                .isEqualTo(PipelineStage.STALE);
        when(current.getSourceDraftRevision()).thenReturn(5L);
        assertThat(resolver.resolve(pipeline, 5, false, true, true, false))
                .isEqualTo(PipelineStage.SUCCEEDED);
        when(pipeline.getCurrentVersionId()).thenReturn(null);
        assertThat(resolver.resolve(pipeline, 5, false, true, true, false))
                .isEqualTo(PipelineStage.READY);
        assertThat(mapper.valueToTree(PipelineStage.NEEDS_INPUT).asText()).isEqualTo("needs_input");
    }
}
