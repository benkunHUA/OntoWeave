package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineBuildMapperTest {

    private final PipelineDatasetRepository datasets = mock(PipelineDatasetRepository.class);
    private final PipelineDatasetVersionRepository versions =
            mock(PipelineDatasetVersionRepository.class);
    private final PipelineBuildMapper mapper = new PipelineBuildMapper(datasets, versions);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsDatasetIdentityWithTwoBatchLookups() {
        UUID outputId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID buildOutputId = UUID.randomUUID();
        PipelineBuildOutputEntity output = mock(PipelineBuildOutputEntity.class);
        when(output.getId()).thenReturn(buildOutputId);
        when(output.getOutputId()).thenReturn(outputId);
        when(output.getResult()).thenReturn(json.createObjectNode());
        when(output.getStats()).thenReturn(json.createObjectNode());
        PipelineDatasetEntity dataset = mock(PipelineDatasetEntity.class);
        when(dataset.getId()).thenReturn(datasetId);
        when(dataset.getOutputId()).thenReturn(outputId);
        PipelineDatasetVersionEntity version = mock(PipelineDatasetVersionEntity.class);
        when(version.getId()).thenReturn(versionId);
        when(version.getBuildOutputId()).thenReturn(buildOutputId);
        when(datasets.findByOutputIdIn(Set.of(outputId))).thenReturn(List.of(dataset));
        when(versions.findByBuildOutputIdIn(Set.of(buildOutputId))).thenReturn(List.of(version));
        PipelineBuildEntity build = mock(PipelineBuildEntity.class);

        BuildResponse response = mapper.toResponse(build, List.of(output));

        assertThat(response.outputs()).hasSize(1);
        assertThat(response.outputs().get(0).datasetId()).isEqualTo(datasetId);
        assertThat(response.outputs().get(0).datasetVersionId()).isEqualTo(versionId);
        verify(datasets).findByOutputIdIn(any());
        verify(versions).findByBuildOutputIdIn(any());
    }
}
