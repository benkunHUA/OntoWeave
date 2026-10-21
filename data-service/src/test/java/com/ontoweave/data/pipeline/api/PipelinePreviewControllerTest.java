package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.execution.preview.PipelinePreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PipelinePreviewControllerTest {

    @Test
    void cancelsPreviewForTheOwnedPipelineAndReturnsNoContent() {
        PipelinePreviewService previews = mock(PipelinePreviewService.class);
        PipelinePreviewController controller = new PipelinePreviewController(previews);
        UUID pipelineId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        var response = controller.cancel(pipelineId, ownerId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(previews).cancel(pipelineId, ownerId);
    }
}
