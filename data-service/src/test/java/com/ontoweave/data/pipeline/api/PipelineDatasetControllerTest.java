package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.source.PipelineDatasetPreviewService;
import com.ontoweave.data.sync.api.dto.DatasetPreviewResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineDatasetControllerTest {

    @Test
    void forwardsPublishedDatasetPreviewArguments() {
        PipelineDatasetPreviewService previews = mock(PipelineDatasetPreviewService.class);
        PipelineDatasetController controller = new PipelineDatasetController(previews);
        UUID pipelineId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        DatasetPreviewResponse expected = new DatasetPreviewResponse(
                datasetId, versionId, 1, 42L, false, List.of(), 0L, List.of(), false
        );
        when(previews.preview(pipelineId, datasetId, versionId, ownerId, 50))
                .thenReturn(expected);

        assertThat(controller.preview(pipelineId, datasetId, versionId, ownerId, 50))
                .isSameAs(expected);
        verify(previews).preview(pipelineId, datasetId, versionId, ownerId, 50);
    }
}
