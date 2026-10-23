package com.ontoweave.data.ontology.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.store.IndexEnqueuer;
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
import com.ontoweave.data.pipeline.execution.build.PipelinePublicationTransaction;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.VersionSource;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.version.DatasetVersionPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OntologyIndexPublishHookTest {

    @Mock
    private DatasetRepository datasets;
    @Mock
    private DatasetVersionRepository versions;
    @Mock
    private SyncRunRepository runs;
    @Mock
    private IcebergCatalogClient iceberg;
    @Mock
    private OutboxWriter outbox;
    @Mock
    private IndexEnqueuer enqueuer;

    @Test
    void defersEnqueueUntilThePublicationTransactionCommits() {
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        TransactionSynchronizationManager.initSynchronization();
        try {
            IndexPublishHook.enqueueAfterCommit(
                    enqueuer, DatasetKind.SYNC, datasetId, versionId
            );
            verifyNoInteractions(enqueuer);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(enqueuer).enqueueOnPublish(DatasetKind.SYNC, datasetId, versionId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void syncPublicationEnqueuesThePublishedVersion() {
        SyncFixture fixture = syncFixture();

        UUID versionId = fixture.publisher().publish(
                fixture.run().getId(), VersionSource.SCHEDULED
        ).orElseThrow().getId();

        verify(enqueuer).enqueueOnPublish(DatasetKind.SYNC, fixture.dataset().getId(), versionId);
    }

    @Test
    void syncPublicationStillSucceedsWhenIndexEnqueueFails() {
        SyncFixture fixture = syncFixture();
        when(enqueuer.enqueueOnPublish(
                eq(DatasetKind.SYNC), eq(fixture.dataset().getId()), any()
        )).thenThrow(new IllegalStateException("index unavailable"));

        assertThat(fixture.publisher().publish(
                fixture.run().getId(), VersionSource.SCHEDULED
        )).isPresent();

        verify(versions).save(any());
        verify(outbox).write(
                eq("dataset"),
                eq(fixture.dataset().getId()),
                eq("dataset.version.published"),
                any()
        );
    }

    @Test
    void pipelinePublicationEnqueuesEveryActivatedOutputVersion() {
        PipelineFixture fixture = pipelineFixture();

        fixture.transaction().publish(fixture.buildId());

        ArgumentCaptor<PipelineDatasetVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(PipelineDatasetVersionEntity.class);
        verify(fixture.versions()).save(versionCaptor.capture());
        verify(enqueuer).enqueueOnPublish(
                DatasetKind.PIPELINE, fixture.dataset().getId(), versionCaptor.getValue().getId()
        );
    }

    @Test
    void pipelinePublicationStillSucceedsWhenIndexEnqueueFails() {
        PipelineFixture fixture = pipelineFixture();
        when(enqueuer.enqueueOnPublish(
                eq(DatasetKind.PIPELINE), eq(fixture.dataset().getId()), any()
        )).thenThrow(new IllegalStateException("index unavailable"));

        PipelinePublicationTransaction.Publication publication =
                fixture.transaction().publish(fixture.buildId());

        assertThat(publication.datasets()).hasSize(1);
        assertThat(fixture.build().getStatus()).isEqualTo(PipelineBuildStatus.SUCCEEDED);
        verify(fixture.outbox()).write(
                eq("pipeline"),
                eq(fixture.build().getPipelineId()),
                eq("pipeline.build.succeeded"),
                any()
        );
    }

    private SyncFixture syncFixture() {
        UUID datasetId = UUID.randomUUID();
        SyncRunEntity run = new SyncRunEntity(
                UUID.randomUUID(), UUID.randomUUID(), datasetId, UUID.randomUUID(),
                SyncMode.SNAPSHOT, "job-" + UUID.randomUUID(), "ontoweave", "namespace",
                "orders", 3600
        );
        DatasetEntity dataset = new DatasetEntity(datasetId, UUID.randomUUID(), "orders");
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(datasets.findForUpdate(datasetId)).thenReturn(Optional.of(dataset));
        when(iceberg.latestSnapshot(any(), any(), any())).thenReturn(Optional.of(snapshot()));
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DatasetVersionPublisher publisher = new DatasetVersionPublisher(
                datasets, versions, runs, iceberg, outbox
        );
        ReflectionTestUtils.setField(publisher, "indexEnqueuer", enqueuer);
        return new SyncFixture(run, dataset, publisher);
    }

    private PipelineFixture pipelineFixture() {
        ObjectMapper mapper = new ObjectMapper();
        UUID buildId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID pipelineVersionId = UUID.randomUUID();
        UUID outputId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        PipelineBuildRepository builds = org.mockito.Mockito.mock(PipelineBuildRepository.class);
        PipelineBuildOutputRepository buildOutputs = org.mockito.Mockito.mock(PipelineBuildOutputRepository.class);
        PipelineDatasetRepository pipelineDatasets = org.mockito.Mockito.mock(PipelineDatasetRepository.class);
        PipelineDatasetVersionRepository pipelineVersions = org.mockito.Mockito.mock(PipelineDatasetVersionRepository.class);
        PipelineRepository pipelines = org.mockito.Mockito.mock(PipelineRepository.class);
        OutboxWriter pipelineOutbox = org.mockito.Mockito.mock(OutboxWriter.class);
        PipelinePublicationTransaction transaction = new PipelinePublicationTransaction(
                builds, buildOutputs, pipelineDatasets, pipelineVersions, pipelines,
                pipelineOutbox, mapper
        );
        ReflectionTestUtils.setField(transaction, "indexEnqueuer", enqueuer);
        PipelineBuildEntity build = new PipelineBuildEntity(
                buildId, pipelineId, pipelineVersionId, PipelineBuildStatus.RUNNING,
                PipelineBuildTrigger.MANUAL, UUID.randomUUID(), UUID.randomUUID(),
                mapper.createObjectNode(), mapper.createObjectNode()
        );
        PipelineBuildOutputEntity output = new PipelineBuildOutputEntity(
                UUID.randomUUID(), buildId, outputId, PipelineOutputType.DATASET,
                PipelineOutputStatus.SUCCEEDED, snapshotResult(mapper), mapper.createObjectNode()
        );
        PipelineDatasetEntity dataset = new PipelineDatasetEntity(
                datasetId, outputId, pipelineId, "orders",
                mapper.createArrayNode(), mapper.createArrayNode()
        );
        PipelineEntity pipeline = new PipelineEntity(pipelineId, UUID.randomUUID(), UUID.randomUUID(), "pipeline");
        when(builds.lockById(buildId)).thenReturn(Optional.of(build));
        when(buildOutputs.findByBuildIdOrderByCreatedAtAsc(buildId)).thenReturn(List.of(output));
        when(pipelineDatasets.findByOutputId(outputId)).thenReturn(Optional.of(dataset));
        when(pipelineVersions.findByBuildOutputId(output.getId())).thenReturn(Optional.empty());
        when(pipelineVersions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pipelineDatasets.activateVersion(eq(datasetId), any())).thenReturn(1);
        when(pipelines.lockById(pipelineId)).thenReturn(Optional.of(pipeline));
        when(pipelines.activateVersion(pipelineId, pipelineVersionId)).thenReturn(1);

        return new PipelineFixture(transaction, build, dataset, buildId, pipelineVersions, pipelineOutbox);
    }

    private IcebergSnapshotInfo snapshot() {
        return new IcebergSnapshotInfo(
                42L, Instant.now(), 2L, 100L,
                JsonNodeFactory.instance.objectNode(), 1, "file:/tmp/table"
        );
    }

    private com.fasterxml.jackson.databind.JsonNode snapshotResult(ObjectMapper mapper) {
        var result = mapper.createObjectNode();
        var snapshot = result.putObject("snapshot");
        snapshot.put("icebergCatalog", "ontoweave");
        snapshot.put("icebergNamespace", "pipeline");
        snapshot.put("icebergTable", "out");
        snapshot.put("icebergSnapshotId", 99L);
        snapshot.put("rowCount", 1L);
        snapshot.put("byteSize", 1L);
        snapshot.put("committedAt", Instant.now().toString());
        return result;
    }

    private record SyncFixture(
            SyncRunEntity run,
            DatasetEntity dataset,
            DatasetVersionPublisher publisher
    ) {
    }

    private record PipelineFixture(
            PipelinePublicationTransaction transaction,
            PipelineBuildEntity build,
            PipelineDatasetEntity dataset,
            UUID buildId,
            PipelineDatasetVersionRepository versions,
            OutboxWriter outbox
    ) {
    }
}
