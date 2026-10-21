package com.ontoweave.data.pipeline.draft;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetWriteMode;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineDraftCommandOutputTest extends PipelineDraftCommandServiceTestSupport {

    @Test
    void setOutputUpdatesExistingRowsWithoutDuplicateInsert() {
        UUID outputId = UUID.randomUUID();
        lockDraft(5, outputDraft(outputId.toString()));
        PipelineOutputEntity output = new PipelineOutputEntity(
                outputId, PIPELINE_ID, NODE_ID, PipelineOutputType.DATASET, "orders"
        );
        PipelineDatasetEntity dataset = new PipelineDatasetEntity(
                UUID.randomUUID(), outputId, PIPELINE_ID, "orders",
                mapper.createArrayNode(), mapper.createArrayNode()
        );
        when(outputs.findByPipelineIdAndNodeId(PIPELINE_ID, NODE_ID)).thenReturn(Optional.of(output));
        when(outputs.findByPipelineIdAndName(PIPELINE_ID, "orders")).thenReturn(Optional.of(output));
        when(datasets.findByOutputId(outputId)).thenReturn(Optional.of(dataset));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(5L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.apply(PIPELINE_ID, OWNER_ID, setOutput(5, "orders"));

        assertThat(response.revision()).isEqualTo(6);
        assertThat(response.definition().path("nodes").get(0).path("config").path("outputId").asText())
                .isEqualTo(outputId.toString());
        verify(outputs, never()).save(any());
        verify(datasets, never()).save(any());
        verify(datasets).saveAndFlush(dataset);
    }

    @Test
    void setOutputRejectsDuplicateNameBeforeCreatingRows() {
        UUID outputId = UUID.randomUUID();
        lockDraft(5, outputDraft(outputId.toString()));
        PipelineOutputEntity existing = new PipelineOutputEntity(
                UUID.randomUUID(), PIPELINE_ID, EDGE_ID, PipelineOutputType.DATASET, "orders"
        );
        when(outputs.findByPipelineIdAndNodeId(PIPELINE_ID, NODE_ID)).thenReturn(Optional.empty());
        when(outputs.findByPipelineIdAndName(PIPELINE_ID, "orders")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.apply(PIPELINE_ID, OWNER_ID, setOutput(5, "orders")))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(409);
                    assertThat(error.code()).isEqualTo("OUTPUT_NAME_CONFLICT");
                });

        verify(outputs, never()).saveAndFlush(any());
        verify(datasets, never()).saveAndFlush(any());
        verify(drafts, never()).updateDefinition(any(), eq(5L), anyString(), anyString(), any());
    }

    @Test
    void setOutputUsesSnapshotAndEmptyArraysByDefault() {
        lockDraft(5, outputDraft(""));
        when(drafts.updateDefinition(eq(PIPELINE_ID), eq(5L), anyString(), eq("user"), eq(OWNER_ID)))
                .thenReturn(1);

        DraftResponse response = service.apply(
                PIPELINE_ID,
                OWNER_ID,
                new SetOutput(5L, operationId(5), turnId(5), NODE_ID, "orders", null, null, null)
        );

        ArgumentCaptor<PipelineDatasetEntity> dataset = ArgumentCaptor.forClass(PipelineDatasetEntity.class);
        verify(datasets).saveAndFlush(dataset.capture());
        assertThat(dataset.getValue().getName()).isEqualTo("orders");
        assertThat(dataset.getValue().getWriteMode()).isEqualTo(PipelineDatasetWriteMode.SNAPSHOT);
        assertThat(dataset.getValue().getPrimaryKeyColumns().isArray()).isTrue();
        assertThat(dataset.getValue().getExpectations().isArray()).isTrue();
        assertThat(dataset.getValue().getPrimaryKeyColumns()).isEmpty();
        assertThat(dataset.getValue().getExpectations()).isEmpty();
        assertThat(response.definition().path("nodes").get(0).path("config").path("outputId").asText())
                .isNotBlank();
    }

    @Test
    void setOutputMapsDatasetNameConstraintToOutputNameConflict() {
        lockDraft(5, outputDraft(""));
        when(outputs.findByPipelineIdAndNodeId(PIPELINE_ID, NODE_ID)).thenReturn(Optional.empty());
        when(outputs.findByPipelineIdAndName(PIPELINE_ID, "orders")).thenReturn(Optional.empty());
        when(datasets.saveAndFlush(any(PipelineDatasetEntity.class))).thenThrow(
                new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_pipeline_dataset_name\""
                )
        );

        assertThatThrownBy(() -> service.apply(PIPELINE_ID, OWNER_ID, setOutput(5, "orders")))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(409);
                    assertThat(error.code()).isEqualTo("OUTPUT_NAME_CONFLICT");
                });

        verify(drafts, never()).updateDefinition(any(), eq(5L), anyString(), anyString(), any());
        verify(editRecorder, never()).recordSemanticEdit(any());
        verify(outbox, never()).write(anyString(), any(), anyString(), any());
    }

    @Test
    void setOutputDoesNotMaskOtherDatasetConstraintFailures() {
        lockDraft(5, outputDraft(""));
        when(outputs.findByPipelineIdAndNodeId(PIPELINE_ID, NODE_ID)).thenReturn(Optional.empty());
        when(outputs.findByPipelineIdAndName(PIPELINE_ID, "orders")).thenReturn(Optional.empty());
        when(datasets.saveAndFlush(any(PipelineDatasetEntity.class))).thenThrow(
                new DataIntegrityViolationException(
                        "violates foreign key constraint \"fk_pipeline_dataset_pipeline\""
                )
        );

        assertThatThrownBy(() -> service.apply(PIPELINE_ID, OWNER_ID, setOutput(5, "orders")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_pipeline_dataset_pipeline");
    }

    @Test
    void staleSetOutputFailsBeforeCreatingOutputRows() {
        lockDraft(6, outputDraft(""));

        assertThatThrownBy(() -> service.apply(PIPELINE_ID, OWNER_ID, setOutput(5, "orders")))
                .isInstanceOf(PipelineRevisionConflict.class);

        verify(outputs, never()).findByPipelineIdAndNodeId(any(), any());
        verify(outputs, never()).saveAndFlush(any());
        verify(datasets, never()).saveAndFlush(any());
    }
}
