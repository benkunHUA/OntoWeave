package com.ontoweave.data.pipeline.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.api.dto.SourceInput;
import com.ontoweave.data.pipeline.api.dto.SourceResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineSourceServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    PipelineRepository pipelines;

    @Mock
    PipelineDatasetRepository datasets;

    @Mock
    PipelineDatasetVersionRepository datasetVersions;

    @Mock
    PipelineBuildOutputRepository buildOutputs;

    @Mock
    PipelineBuildRepository builds;

    @Mock
    PipelineVersionRepository pipelineVersions;

    @Mock
    DatasetMetadataQuery metadataQuery;

    private PipelineSourceService service;
    private UUID ownerId;
    private UUID pipelineId;
    private UUID datasetId;
    private UUID outputId;
    private UUID sourceVersionId;
    private UUID buildOutputId;
    private UUID buildId;
    private UUID pipelineVersionId;

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

    @Test
    void usesBuildTimeSnapshotAndResolvedVersionAfterTheInputIsRenamed() {
        UUID inputDatasetId = UUID.randomUUID();
        UUID inputVersionId = UUID.randomUUID();
        givenSourceChain(resolvedInputs(resolvedInput(
                "sync",
                inputDatasetId,
                inputVersionId,
                12,
                "hash-1",
                snapshot("orders", 12, "prod-mysql", "sales", "orders")
        )));
        when(metadataQuery.batchLoad(anyList(), eq(ownerId))).thenReturn(Map.of(
                new DatasetRef("sync", inputDatasetId),
                new DatasetMetadata(
                        "orders-current",
                        99,
                        UUID.randomUUID(),
                        "renamed-connection",
                        "renamed_schema",
                        "renamed_table",
                        MetadataStatus.AVAILABLE
                )
        ));

        SourceResponse response = service.get(pipelineId, datasetId, sourceVersionId, ownerId);

        assertThat(response.inputs()).singleElement().satisfies(input -> {
            assertThat(input.datasetVersionId()).isEqualTo(inputVersionId);
            assertThat(input.datasetVersionNumber()).isEqualTo(12);
            assertThat(input.datasetName()).isEqualTo("orders");
            assertThat(input.connectionName()).isEqualTo("prod-mysql");
            assertThat(input.sourceSchemaName()).isEqualTo("sales");
            assertThat(input.sourceTableName()).isEqualTo("orders");
            assertThat(input.metadataStatus()).isEqualTo(MetadataStatus.AVAILABLE);
        });
        verify(metadataQuery, times(1)).batchLoad(anyList(), eq(ownerId));
    }

    @Test
    void usesCurrentVersionWhenVersionIdIsOmitted() {
        givenSourceChain(resolvedInputs());

        service.get(pipelineId, datasetId, null, ownerId);

        verify(datasetVersions).findByIdAndDatasetId(sourceVersionId, datasetId);
    }

    @Test
    void rejectsUnpublishedVersionAndDatasetOutsideThePipeline() {
        when(datasets.findByIdAndPipelineId(datasetId, pipelineId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(pipelineId, datasetId, sourceVersionId, ownerId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status().value()).isEqualTo(404));

        PipelineDatasetEntity dataset = org.mockito.Mockito.mock(PipelineDatasetEntity.class);
        when(datasets.findByIdAndPipelineId(datasetId, pipelineId)).thenReturn(Optional.of(dataset));
        PipelineDatasetVersionEntity unpublished = org.mockito.Mockito.mock(PipelineDatasetVersionEntity.class);
        when(unpublished.getPublishedAt()).thenReturn(null);
        when(datasetVersions.findByIdAndDatasetId(sourceVersionId, datasetId))
                .thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> service.get(pipelineId, datasetId, sourceVersionId, ownerId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.status().value()).isEqualTo(404));
    }

    @Test
    void mapsAvailableRestrictedAndMissingMetadataAndLoadsThemInOneBatch() {
        UUID availableId = UUID.randomUUID();
        UUID restrictedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        givenSourceChain(resolvedInputs(
                resolvedInput("sync", availableId, UUID.randomUUID(), 1, "hash-a", null),
                resolvedInput("sync", restrictedId, UUID.randomUUID(), 2, "hash-r", null),
                resolvedInput("sync", missingId, UUID.randomUUID(), 3, "hash-m",
                        snapshot("saved-name", 3, "saved-conn", "saved_schema", "saved_table"))
        ));
        Map<DatasetRef, DatasetMetadata> metadata = new LinkedHashMap<>();
        metadata.put(new DatasetRef("sync", availableId), new DatasetMetadata(
                "available-name", 1, UUID.randomUUID(), "available-conn", "available_schema",
                "available_table", MetadataStatus.AVAILABLE
        ));
        metadata.put(new DatasetRef("sync", restrictedId),
                DatasetMetadata.restricted());
        when(metadataQuery.batchLoad(anyList(), eq(ownerId))).thenReturn(metadata);

        SourceResponse response = service.get(pipelineId, datasetId, sourceVersionId, ownerId);

        assertThat(response.inputs()).hasSize(3);
        assertThat(response.inputs().get(0).datasetName()).isEqualTo("available-name");
        assertThat(response.inputs().get(0).connectionName()).isEqualTo("available-conn");
        assertThat(response.inputs().get(0).metadataStatus()).isEqualTo(MetadataStatus.AVAILABLE);

        SourceInput restricted = response.inputs().get(1);
        assertThat(restricted.datasetName()).isNull();
        assertThat(restricted.connectionName()).isNull();
        assertThat(restricted.sourceSchemaName()).isNull();
        assertThat(restricted.sourceTableName()).isNull();
        assertThat(restricted.metadataStatus()).isEqualTo(MetadataStatus.RESTRICTED);

        SourceInput missing = response.inputs().get(2);
        assertThat(missing.datasetName()).isEqualTo("saved-name");
        assertThat(missing.connectionName()).isEqualTo("saved-conn");
        assertThat(missing.sourceSchemaName()).isEqualTo("saved_schema");
        assertThat(missing.sourceTableName()).isEqualTo("saved_table");
        assertThat(missing.metadataStatus()).isEqualTo(MetadataStatus.MISSING);

        verify(metadataQuery, times(1)).batchLoad(anyList(), eq(ownerId));
    }

    @Test
    void ignoresMalformedResolvedInputsWithoutFallingBackToCurrentInputs() {
        ObjectNode malformed = MAPPER.createObjectNode();
        malformed.put("datasetKind", "sync");
        malformed.put("datasetId", "not-a-uuid");
        givenSourceChain(MAPPER.createArrayNode().add(malformed));

        SourceResponse response = service.get(pipelineId, datasetId, sourceVersionId, ownerId);

        assertThat(response.inputs()).isEmpty();
    }

    private void givenSourceChain(ArrayNode resolvedInputs) {
        givenDatasetAndVersion();
        PipelineBuildOutputEntity buildOutput = org.mockito.Mockito.mock(PipelineBuildOutputEntity.class);
        when(buildOutput.getBuildId()).thenReturn(buildId);
        when(buildOutput.getOutputId()).thenReturn(outputId);
        when(buildOutputs.findById(buildOutputId)).thenReturn(Optional.of(buildOutput));

        PipelineBuildEntity build = org.mockito.Mockito.mock(PipelineBuildEntity.class);
        when(build.getPipelineId()).thenReturn(pipelineId);
        when(build.getPipelineVersionId()).thenReturn(pipelineVersionId);
        when(build.getStatus()).thenReturn(PipelineBuildStatus.SUCCEEDED);
        when(build.getTriggerType()).thenReturn(PipelineBuildTrigger.MANUAL);
        when(build.getStartedAt()).thenReturn(Instant.parse("2026-10-20T10:00:00Z"));
        when(build.getFinishedAt()).thenReturn(Instant.parse("2026-10-20T10:01:00Z"));
        when(build.getResolvedInputs()).thenReturn(resolvedInputs);
        when(builds.findById(buildId)).thenReturn(Optional.of(build));

        PipelineVersionEntity pipelineVersion = org.mockito.Mockito.mock(PipelineVersionEntity.class);
        when(pipelineVersion.getVersionNumber()).thenReturn(7);
        when(pipelineVersion.getCreatedAt()).thenReturn(Instant.parse("2026-10-20T09:59:00Z"));
        when(pipelineVersions.findByIdAndPipelineId(pipelineVersionId, pipelineId))
                .thenReturn(Optional.of(pipelineVersion));
    }

    private void givenDatasetAndVersion() {
        PipelineDatasetEntity dataset = org.mockito.Mockito.mock(PipelineDatasetEntity.class);
        when(dataset.getOutputId()).thenReturn(outputId);
        when(dataset.getName()).thenReturn("orders_clean");
        lenient().when(dataset.getCurrentVersionId()).thenReturn(sourceVersionId);
        when(datasets.findByIdAndPipelineId(datasetId, pipelineId)).thenReturn(Optional.of(dataset));

        PipelineDatasetVersionEntity version = version(true);
        when(datasetVersions.findByIdAndDatasetId(sourceVersionId, datasetId))
                .thenReturn(Optional.of(version));
    }

    private PipelineDatasetVersionEntity version(boolean published) {
        PipelineDatasetVersionEntity version = org.mockito.Mockito.mock(PipelineDatasetVersionEntity.class);
        when(version.getBuildOutputId()).thenReturn(buildOutputId);
        when(version.getVersionNumber()).thenReturn(3);
        when(version.getRowCount()).thenReturn(1200L);
        when(version.getPublishedAt()).thenReturn(
                published ? Instant.parse("2026-10-20T10:02:00Z") : null
        );
        return version;
    }

    private ArrayNode resolvedInputs(ObjectNode... inputs) {
        ArrayNode array = MAPPER.createArrayNode();
        for (ObjectNode input : inputs) {
            array.add(input);
        }
        return array;
    }

    private ObjectNode resolvedInput(
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

    private ObjectNode snapshot(
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
