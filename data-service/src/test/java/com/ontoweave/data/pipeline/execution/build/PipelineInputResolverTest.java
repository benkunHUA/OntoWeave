package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.domain.ConnectionKind;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.domain.VersionSource;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineInputResolverTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final long REVISION = 4L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineDraftRepository drafts = mock(PipelineDraftRepository.class);
    private final DatasetRepository datasets = mock(DatasetRepository.class);
    private final DatasetVersionRepository versions = mock(DatasetVersionRepository.class);
    private final DataConnectionRepository connections = mock(DataConnectionRepository.class);
    private final SyncTaskRepository tasks = mock(SyncTaskRepository.class);
    private final PipelineInputResolver resolver = new PublishedPipelineInputResolver(
            drafts, datasets, versions, connections, tasks
    );

    @Test
    void resolvesPublishedVersionWithFrozenSnapshotAndDisplaySnapshot() {
        UUID datasetId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        PipelineDraftEntity draft = draft(datasetId);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        DatasetEntity dataset = dataset(datasetId, connectionId, versionId);
        when(datasets.findAllById(anyIterable())).thenReturn(List.of(dataset));
        when(versions.findAllById(anyIterable())).thenReturn(List.of(version(datasetId, versionId)));
        when(connections.findAllById(anyIterable()))
                .thenReturn(List.of(new DataConnectionEntity(
                        connectionId, UUID.randomUUID(), UUID.randomUUID(), "prod-mysql", ConnectionKind.MYSQL
                )));
        when(tasks.findByDatasetIdIn(anyCollection())).thenReturn(List.of(task(datasetId, connectionId)));

        List<ResolvedInput> resolved = resolver.resolve(PIPELINE_ID, REVISION);

        assertThat(resolved).singleElement().satisfies(input -> {
            assertThat(input.datasetVersionId()).isEqualTo(versionId);
            assertThat(input.datasetVersionNumber()).isEqualTo(12);
            assertThat(input.icebergSnapshotId()).isEqualTo(100L);
            assertThat(input.icebergCatalog()).isEqualTo("ontoweave");
            assertThat(input.icebergNamespace()).isEqualTo("sales");
            assertThat(input.icebergTable()).isEqualTo("orders");
            assertThat(input.schemaHash()).isNotBlank();
            assertThat(input.displaySnapshot().connectionName()).isEqualTo("prod-mysql");
            assertThat(input.displaySnapshot().sourceSchemaName()).isEqualTo("sales");
            assertThat(input.displaySnapshot().sourceTableName()).isEqualTo("orders");
        });
    }

    @Test
    void rejectsInputWithoutPublishedVersion() {
        UUID datasetId = UUID.randomUUID();
        PipelineDraftEntity draft = draft(datasetId);
        when(drafts.findById(PIPELINE_ID)).thenReturn(Optional.of(draft));
        DatasetEntity dataset = new DatasetEntity(datasetId, UUID.randomUUID(), "orders");
        when(datasets.findAllById(anyIterable())).thenReturn(List.of(dataset));

        assertThatThrownBy(() -> resolver.resolve(PIPELINE_ID, REVISION))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.code()).isEqualTo("INPUT_VERSION_UNRESOLVED");
                });
    }

    private PipelineDraftEntity draft(UUID datasetId) {
        PipelineDraftEntity draft = mock(PipelineDraftEntity.class);
        when(draft.getRevision()).thenReturn(REVISION);
        when(draft.getDefinition()).thenReturn(definition(datasetId.toString()));
        return draft;
    }

    private ObjectNode definition(String datasetId) {
        ObjectNode definition = mapper.createObjectNode();
        definition.put("schemaVersion", 1);
        ObjectNode node = definition.putArray("nodes").addObject();
        node.put("id", UUID.randomUUID().toString());
        node.put("type", "input");
        node.put("name", "orders");
        node.putObject("config")
                .put("datasetKind", "sync")
                .put("datasetId", datasetId);
        definition.putArray("edges");
        return definition;
    }

    private DatasetEntity dataset(UUID datasetId, UUID connectionId, UUID versionId) {
        DatasetEntity dataset = new DatasetEntity(datasetId, connectionId, "orders");
        dataset.activateVersion(versionId, "ontoweave", "sales", "orders");
        return dataset;
    }

    private DatasetVersionEntity version(UUID datasetId, UUID versionId) {
        DatasetVersionEntity version = new DatasetVersionEntity(
                versionId,
                datasetId,
                12,
                "ontoweave",
                "sales",
                "orders",
                100L,
                25L,
                false,
                1024L,
                mapper.createObjectNode().set("fields", mapper.createArrayNode()
                        .add(mapper.createObjectNode().put("name", "id").put("type", "long").put("required", true))),
                1,
                Instant.now(),
                VersionSource.SNAPSHOT_END,
                UUID.randomUUID(),
                "file:/tmp/orders"
        );
        ReflectionTestUtils.setField(version, "publishedAt", Instant.now());
        return version;
    }

    private SyncTaskEntity task(UUID datasetId, UUID connectionId) {
        return new SyncTaskEntity(
                UUID.randomUUID(),
                connectionId,
                datasetId,
                "sales",
                "orders",
                SyncMode.SNAPSHOT,
                86400,
                mapper.createArrayNode()
        );
    }
}
