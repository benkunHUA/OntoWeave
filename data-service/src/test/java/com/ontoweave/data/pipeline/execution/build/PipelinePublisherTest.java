package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineBuildTrigger;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelinePublisherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineBuildRepository builds = mock(PipelineBuildRepository.class);
    private final PipelineBuildOutputRepository buildOutputs = mock(PipelineBuildOutputRepository.class);
    private final PipelineDatasetRepository datasets = mock(PipelineDatasetRepository.class);
    private final PipelineDatasetVersionRepository versions = mock(PipelineDatasetVersionRepository.class);
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final OutboxWriter outbox = mock(OutboxWriter.class);
    private final PipelinePublicationTransaction transaction = new PipelinePublicationTransaction(
            builds, buildOutputs, datasets, versions, pipelines, outbox, mapper
    );

    @Test
    void publishesAllOutputsAndSwitchesPointersInOneTransaction() {
        Fixture fixture = fixture();
        when(builds.lockById(fixture.buildId())).thenReturn(Optional.of(fixture.build()));
        when(buildOutputs.findByBuildIdOrderByCreatedAtAsc(fixture.buildId()))
                .thenReturn(List.of(fixture.outputA(), fixture.outputB()));
        when(datasets.findByOutputId(fixture.outputA().getOutputId()))
                .thenReturn(Optional.of(fixture.datasetA()));
        when(datasets.findByOutputId(fixture.outputB().getOutputId()))
                .thenReturn(Optional.of(fixture.datasetB()));
        when(versions.findTopByDatasetIdOrderByVersionNumberDesc(fixture.datasetA().getId()))
                .thenReturn(Optional.of(version(fixture.datasetA().getId(), 3)));
        when(versions.findTopByDatasetIdOrderByVersionNumberDesc(fixture.datasetB().getId()))
                .thenReturn(Optional.empty());
        when(versions.findByBuildOutputId(any())).thenReturn(Optional.empty());
        when(versions.save(any(PipelineDatasetVersionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pipelines.activateVersion(
                fixture.build().getPipelineId(), fixture.build().getPipelineVersionId()
        )).thenReturn(1);

        PipelinePublicationTransaction.Publication publication = transaction.publish(fixture.buildId());

        assertThat(publication.datasets()).hasSize(2);
        verify(datasets).activateVersion(eq(fixture.datasetA().getId()), any());
        verify(datasets).activateVersion(eq(fixture.datasetB().getId()), any());
        verify(pipelines).activateVersion(
                fixture.build().getPipelineId(), fixture.build().getPipelineVersionId()
        );
        verify(outbox).write(eq("pipeline"), eq(fixture.build().getPipelineId()),
                eq("pipeline.build.succeeded"), any());
        assertThat(fixture.build().getStatus()).isEqualTo(PipelineBuildStatus.SUCCEEDED);
    }

    @Test
    void refusesToPublishAfterCancellationWasRequested() {
        Fixture fixture = fixture();
        fixture.build().requestCancel(java.time.Instant.now());
        when(builds.lockById(fixture.buildId())).thenReturn(Optional.of(fixture.build()));

        assertThatThrownBy(() -> transaction.publish(fixture.buildId()))
                .isInstanceOfSatisfying(PipelineBuildException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(PipelineBuildConstants.CODE_CANCELED));

        verify(datasets, never()).activateVersion(any(), any());
        verify(pipelines, never()).activateVersion(any(), any());
        verify(outbox, never()).write(any(), any(), any(), any());
    }

    @Test
    void fastForwardsAndDropsBranchesAfterTheTransaction() {
        UUID buildId = UUID.randomUUID();
        UUID datasetA = UUID.randomUUID();
        UUID datasetB = UUID.randomUUID();
        PipelinePublicationTransaction publication = mock(PipelinePublicationTransaction.class);
        PipelineDatasetWriter writer = mock(PipelineDatasetWriter.class);
        PipelinePublisher publisher = new PipelinePublisherImpl(publication, writer);
        when(publication.publish(buildId)).thenReturn(new PipelinePublicationTransaction.Publication(
                UUID.randomUUID(),
                List.of(
                        new PipelinePublicationTransaction.PublishedDataset(datasetA, "build_" + buildId, 11L),
                        new PipelinePublicationTransaction.PublishedDataset(datasetB, "build_" + buildId, 12L)
                )
        ));
        doThrow(new IllegalStateException("warehouse unavailable"))
                .when(writer).fastForwardMain(datasetA, 11L);

        publisher.publish(buildId);

        verify(writer).fastForwardMain(datasetA, 11L);
        verify(writer).dropBranch(datasetA, "build_" + buildId);
        verify(writer).fastForwardMain(datasetB, 12L);
        verify(writer).dropBranch(datasetB, "build_" + buildId);
    }

    @Test
    void doesNotFastForwardWhenPublishTransactionFails() {
        UUID buildId = UUID.randomUUID();
        PipelinePublicationTransaction publication = mock(PipelinePublicationTransaction.class);
        PipelineDatasetWriter writer = mock(PipelineDatasetWriter.class);
        PipelinePublisher publisher = new PipelinePublisherImpl(publication, writer);
        when(publication.publish(buildId)).thenThrow(new IllegalStateException("db error"));

        try {
            publisher.publish(buildId);
        } catch (IllegalStateException expected) {
            // expected
        }

        verify(writer, never()).fastForwardMain(any(), anyLong());
        verify(writer, never()).dropBranch(any(), any());
    }

    private Fixture fixture() {
        UUID buildId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID outputAId = UUID.randomUUID();
        UUID outputBId = UUID.randomUUID();
        UUID datasetAId = UUID.randomUUID();
        UUID datasetBId = UUID.randomUUID();
        PipelineBuildEntity build = new PipelineBuildEntity(
                buildId, pipelineId, versionId, PipelineBuildStatus.RUNNING,
                PipelineBuildTrigger.MANUAL, UUID.randomUUID(), UUID.randomUUID(),
                mapper.createArrayNode(), mapper.createObjectNode()
        );
        PipelineBuildOutputEntity outputA = output(buildId, outputAId, datasetAId);
        PipelineBuildOutputEntity outputB = output(buildId, outputBId, datasetBId);
        PipelineDatasetEntity datasetA = new PipelineDatasetEntity(
                datasetAId, outputAId, pipelineId, "orders",
                mapper.createArrayNode(), mapper.createArrayNode()
        );
        PipelineDatasetEntity datasetB = new PipelineDatasetEntity(
                datasetBId, outputBId, pipelineId, "users",
                mapper.createArrayNode(), mapper.createArrayNode()
        );
        PipelineEntity pipeline = new PipelineEntity(pipelineId, UUID.randomUUID(), UUID.randomUUID(), "pipeline");
        return new Fixture(buildId, build, outputA, outputB, datasetA, datasetB, pipeline);
    }

    private PipelineBuildOutputEntity output(UUID buildId, UUID outputId, UUID datasetId) {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode snapshot = result.putObject("snapshot");
        snapshot.put("icebergCatalog", "ontoweave");
        snapshot.put("icebergNamespace", "pipeline");
        snapshot.put("icebergTable", "t_" + datasetId);
        snapshot.put("branch", "build_" + buildId);
        snapshot.put("icebergSnapshotId", 99L);
        snapshot.put("rowCount", 42L);
        snapshot.put("byteSize", 1024L);
        snapshot.put("committedAt", "2026-10-21T00:00:00Z");
        snapshot.put("schema", mapper.createObjectNode().set(
                "fields", mapper.createArrayNode().add(mapper.createObjectNode()
                        .put("name", "id").put("type", "long").put("required", true))
        ));
        return new PipelineBuildOutputEntity(
                UUID.randomUUID(), buildId, outputId, PipelineOutputType.DATASET,
                PipelineOutputStatus.SUCCEEDED, result, mapper.createObjectNode()
        );
    }

    private PipelineDatasetVersionEntity version(UUID datasetId, int number) {
        return new PipelineDatasetVersionEntity(
                UUID.randomUUID(), datasetId, UUID.randomUUID(), number,
                "ontoweave", "pipeline", "t", 1L,
                mapper.createObjectNode(), 1L, 1L, java.time.Instant.now()
        );
    }

    private record Fixture(
            UUID buildId,
            PipelineBuildEntity build,
            PipelineBuildOutputEntity outputA,
            PipelineBuildOutputEntity outputB,
            PipelineDatasetEntity datasetA,
            PipelineDatasetEntity datasetB,
            PipelineEntity pipeline
    ) {
    }
}
