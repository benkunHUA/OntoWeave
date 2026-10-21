package com.ontoweave.data.pipeline.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineBuildTrigger;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
abstract class PipelineSourceServiceTestSupport {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    protected PipelineRepository pipelines;

    @Mock
    protected PipelineDatasetRepository datasets;

    @Mock
    protected PipelineDatasetVersionRepository datasetVersions;

    @Mock
    protected PipelineBuildOutputRepository buildOutputs;

    @Mock
    protected PipelineBuildRepository builds;

    @Mock
    protected PipelineVersionRepository pipelineVersions;

    @Mock
    protected DatasetMetadataQuery metadataQuery;

    protected PipelineSourceService service;
    protected UUID ownerId;
    protected UUID pipelineId;
    protected UUID datasetId;
    protected UUID outputId;
    protected UUID sourceVersionId;
    protected UUID buildOutputId;
    protected UUID buildId;
    protected UUID pipelineVersionId;

    @BeforeEach
    void setUp() {
        service = new PipelineSourceService(
                pipelines,
                datasets,
                datasetVersions,
                buildOutputs,
                builds,
                pipelineVersions,
                metadataQuery
        );
        ownerId = UUID.randomUUID();
        pipelineId = UUID.randomUUID();
        datasetId = UUID.randomUUID();
        outputId = UUID.randomUUID();
        sourceVersionId = UUID.randomUUID();
        buildOutputId = UUID.randomUUID();
        buildId = UUID.randomUUID();
        pipelineVersionId = UUID.randomUUID();

        PipelineEntity pipeline = new PipelineEntity(pipelineId, ownerId, UUID.randomUUID(), "pipeline");
        when(pipelines.findByIdAndOwnerId(pipelineId, ownerId)).thenReturn(Optional.of(pipeline));
    }

    protected void givenSourceChain(ArrayNode resolvedInputs) {
        givenDatasetAndVersion();
        PipelineBuildOutputEntity buildOutput = mock(PipelineBuildOutputEntity.class);
        when(buildOutput.getBuildId()).thenReturn(buildId);
        when(buildOutput.getOutputId()).thenReturn(outputId);
        when(buildOutputs.findById(buildOutputId)).thenReturn(Optional.of(buildOutput));

        PipelineBuildEntity build = mock(PipelineBuildEntity.class);
        when(build.getPipelineId()).thenReturn(pipelineId);
        when(build.getPipelineVersionId()).thenReturn(pipelineVersionId);
        when(build.getStatus()).thenReturn(PipelineBuildStatus.SUCCEEDED);
        when(build.getTriggerType()).thenReturn(PipelineBuildTrigger.MANUAL);
        when(build.getStartedAt()).thenReturn(Instant.parse("2026-10-20T10:00:00Z"));
        when(build.getFinishedAt()).thenReturn(Instant.parse("2026-10-20T10:01:00Z"));
        when(build.getLogUri()).thenReturn("http://flink:8081/#/job/job-1/exceptions");
        when(build.getResolvedInputs()).thenReturn(resolvedInputs);
        when(builds.findById(buildId)).thenReturn(Optional.of(build));

        PipelineVersionEntity pipelineVersion = mock(PipelineVersionEntity.class);
        when(pipelineVersion.getVersionNumber()).thenReturn(7);
        when(pipelineVersion.getCreatedAt()).thenReturn(Instant.parse("2026-10-20T09:59:00Z"));
        when(pipelineVersions.findByIdAndPipelineId(pipelineVersionId, pipelineId))
                .thenReturn(Optional.of(pipelineVersion));
    }

    protected void givenDatasetAndVersion() {
        PipelineDatasetEntity dataset = mock(PipelineDatasetEntity.class);
        when(dataset.getOutputId()).thenReturn(outputId);
        when(dataset.getName()).thenReturn("orders_clean");
        lenient().when(dataset.getCurrentVersionId()).thenReturn(sourceVersionId);
        when(datasets.findByIdAndPipelineId(datasetId, pipelineId)).thenReturn(Optional.of(dataset));

        PipelineDatasetVersionEntity version = mock(PipelineDatasetVersionEntity.class);
        when(version.getBuildOutputId()).thenReturn(buildOutputId);
        when(version.getVersionNumber()).thenReturn(3);
        when(version.getRowCount()).thenReturn(1200L);
        when(version.getPublishedAt()).thenReturn(Instant.parse("2026-10-20T10:02:00Z"));
        when(datasetVersions.findByIdAndDatasetId(sourceVersionId, datasetId))
                .thenReturn(Optional.of(version));
    }

    protected ArrayNode resolvedInputs(ObjectNode... inputs) {
        ArrayNode array = MAPPER.createArrayNode();
        for (ObjectNode input : inputs) {
            array.add(input);
        }
        return array;
    }

    protected ObjectNode resolvedInput(
            String datasetKind,
            UUID inputDatasetId,
            UUID datasetVersionId,
            int datasetVersionNumber,
            String schemaHash,
            ObjectNode displaySnapshot
    ) {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("datasetKind", datasetKind);
        input.put("datasetId", inputDatasetId.toString());
        input.put("datasetVersionId", datasetVersionId.toString());
        input.put("datasetVersionNumber", datasetVersionNumber);
        input.put("schemaHash", schemaHash);
        if (displaySnapshot != null) {
            input.set("displaySnapshot", displaySnapshot);
        }
        return input;
    }

    protected ObjectNode snapshot(
            String datasetName,
            int datasetVersionNumber,
            String connectionName,
            String sourceSchemaName,
            String sourceTableName
    ) {
        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.put("datasetName", datasetName);
        snapshot.put("datasetVersionNumber", datasetVersionNumber);
        snapshot.put("connectionId", UUID.randomUUID().toString());
        snapshot.put("connectionName", connectionName);
        snapshot.put("sourceSchemaName", sourceSchemaName);
        snapshot.put("sourceTableName", sourceTableName);
        return snapshot;
    }
}
