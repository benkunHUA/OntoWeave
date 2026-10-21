package com.ontoweave.data.pipeline.check;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.graph.InputSchemaResolver;
import com.ontoweave.data.pipeline.graph.PipelineGraphValidator;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PipelineCheckOwnershipTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    @Test
    void assertOwnedOnlyReadsPipelineOwnership() {
        PipelineRepository pipelines = mock(PipelineRepository.class);
        PipelineDraftRepository drafts = mock(PipelineDraftRepository.class);
        PipelineOutputRepository outputs = mock(PipelineOutputRepository.class);
        PipelineDatasetRepository datasets = mock(PipelineDatasetRepository.class);
        PipelineCheckServiceImpl service = service(pipelines, drafts, outputs, datasets);
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(mock(PipelineEntity.class)));

        service.assertOwned(PIPELINE_ID, OWNER_ID);

        verify(pipelines).findByIdAndOwnerId(PIPELINE_ID, OWNER_ID);
        verifyNoInteractions(drafts, outputs, datasets);
    }

    @Test
    void assertOwnedRejectsPipelinesOwnedBySomeoneElse() {
        PipelineRepository pipelines = mock(PipelineRepository.class);
        PipelineDraftRepository drafts = mock(PipelineDraftRepository.class);
        PipelineOutputRepository outputs = mock(PipelineOutputRepository.class);
        PipelineDatasetRepository datasets = mock(PipelineDatasetRepository.class);
        PipelineCheckServiceImpl service = service(pipelines, drafts, outputs, datasets);
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertOwned(PIPELINE_ID, OWNER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("NOT_FOUND");
                });
    }

    private PipelineCheckServiceImpl service(
            PipelineRepository pipelines,
            PipelineDraftRepository drafts,
            PipelineOutputRepository outputs,
            PipelineDatasetRepository datasets
    ) {
        return new PipelineCheckServiceImpl(
                pipelines,
                drafts,
                outputs,
                datasets,
                mock(PipelineNodeCatalog.class),
                mock(PipelineGraphValidator.class),
                mock(InputSchemaResolver.class),
                mock(PipelineStageResolver.class)
        );
    }
}
