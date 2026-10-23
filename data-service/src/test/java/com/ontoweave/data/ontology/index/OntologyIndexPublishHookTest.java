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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    void syncPublicationEnqueuesThePublishedVersion() {
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

        UUID versionId = publisher.publish(run.getId(), VersionSource.SCHEDULED).orElseThrow().getId();

        verify(enqueuer).enqueueOnPublish(DatasetKind.SYNC, datasetId, versionId);
    }

    @Test
    void pipelinePublicationEnqueuesEveryActivatedOutputVersion() {
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

        transaction.publish(buildId);

        ArgumentCaptor<PipelineDatasetVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(PipelineDatasetVersionEntity.class);
        verify(pipelineVersions).save(versionCaptor.capture());
        verify(enqueuer).enqueueOnPublish(
                DatasetKind.PIPELINE, datasetId, versionCaptor.getValue().getId()
        );
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
}
