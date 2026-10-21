package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.pipeline.catalog.CanonicalJson;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
import com.ontoweave.data.pipeline.graph.DraftNode;
import com.ontoweave.data.pipeline.graph.InputSchemaResolver;
import com.ontoweave.data.pipeline.graph.PipelineDraftGraph;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.source.DisplaySnapshot;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PublishedPipelineInputResolver implements PipelineInputResolver {

    private static final String DATASET_KIND = "sync";

    private final PipelineDraftRepository drafts;
    private final DatasetRepository datasets;
    private final DatasetVersionRepository versions;
    private final DataConnectionRepository connections;
    private final SyncTaskRepository tasks;

    public PublishedPipelineInputResolver(
            PipelineDraftRepository drafts,
            DatasetRepository datasets,
            DatasetVersionRepository versions,
            DataConnectionRepository connections,
            SyncTaskRepository tasks
    ) {
        this.drafts = drafts;
        this.datasets = datasets;
        this.versions = versions;
        this.connections = connections;
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResolvedInput> resolve(UUID pipelineId, long draftRevision) {
        PipelineDraftEntity draft = drafts.findById(pipelineId)
                .orElseThrow(() -> notFound("流程或草稿不存在"));
        if (draft.getRevision() != draftRevision) {
            throw new PipelineRevisionConflict();
        }
        PipelineDraftGraph graph = PipelineDraftGraph.from(draft.getDefinition());
        List<DraftNode> inputNodes = graph.nodes().stream()
                .filter(node -> "input".equals(node.type()))
                .toList();
        if (inputNodes.isEmpty()) {
            return List.of();
        }

        Map<UUID, UUID> versionByDataset = new LinkedHashMap<>();
        Set<UUID> datasetIds = new LinkedHashSet<>();
        for (DraftNode node : inputNodes) {
            UUID datasetId = datasetId(node);
            if (datasetId != null) {
                datasetIds.add(datasetId);
            }
        }
        Map<UUID, DatasetEntity> datasetsById = new HashMap<>();
        for (DatasetEntity dataset : datasets.findAllById(datasetIds)) {
            datasetsById.put(dataset.getId(), dataset);
            if (dataset.getCurrentVersionId() != null) {
                versionByDataset.put(dataset.getId(), dataset.getCurrentVersionId());
            }
        }
        Map<UUID, DatasetVersionEntity> versionsById = new HashMap<>();
        if (!versionByDataset.isEmpty()) {
            for (DatasetVersionEntity version : versions.findAllById(versionByDataset.values())) {
                versionsById.put(version.getId(), version);
            }
        }
        Set<UUID> connectionIds = datasetsById.values().stream()
                .map(DatasetEntity::getConnectionId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, DataConnectionEntity> connectionsById = new HashMap<>();
        if (!connectionIds.isEmpty()) {
            for (DataConnectionEntity connection : connections.findAllById(connectionIds)) {
                connectionsById.put(connection.getId(), connection);
            }
        }
        Map<UUID, SyncTaskEntity> tasksByDataset = new HashMap<>();
        if (!datasetIds.isEmpty()) {
            for (SyncTaskEntity task : tasks.findByDatasetIdIn(datasetIds)) {
                tasksByDataset.put(task.getDatasetId(), task);
            }
        }

        Map<UUID, ResolvedInput> resolved = new LinkedHashMap<>();
        for (DraftNode node : inputNodes) {
            UUID datasetId = datasetId(node);
            if (datasetId == null) {
                throw unresolved(node, "输入节点缺少合法的 datasetId");
            }
            DatasetEntity dataset = datasetsById.get(datasetId);
            UUID versionId = versionByDataset.get(datasetId);
            DatasetVersionEntity version = versionId == null ? null : versionsById.get(versionId);
            if (dataset == null || version == null
                    || !dataset.getId().equals(version.getDatasetId())
                    || version.getPublishedAt() == null
                    || version.getSchemaJson() == null) {
                throw unresolved(node, "输入数据集没有已发布版本: " + datasetId);
            }
            ResolvedInput input = toResolvedInput(
                    dataset,
                    version,
                    connectionsById.get(dataset.getConnectionId()),
                    tasksByDataset.get(dataset.getId())
            );
            resolved.putIfAbsent(version.getId(), input);
        }
        return new ArrayList<>(resolved.values());
    }

    private ResolvedInput toResolvedInput(
            DatasetEntity dataset,
            DatasetVersionEntity version,
            DataConnectionEntity connection,
            SyncTaskEntity task
    ) {
        DisplaySnapshot snapshot = new DisplaySnapshot(
                dataset.getName(),
                version.getVersionNumber(),
                dataset.getConnectionId(),
                connection == null ? null : connection.getName(),
                task == null ? null : task.getSchemaName(),
                task == null ? null : task.getTableName()
        );
        return new ResolvedInput(
                DATASET_KIND,
                dataset.getId(),
                version.getId(),
                version.getVersionNumber(),
                CanonicalJson.sha256(version.getSchemaJson()),
                version.getIcebergCatalog(),
                version.getIcebergNamespace(),
                version.getIcebergTable(),
                version.getIcebergSnapshotId(),
                snapshot
        );
    }

    private UUID datasetId(DraftNode node) {
        JsonNode config = node.config();
        if (config == null || !DATASET_KIND.equals(config.path("datasetKind").asText(null))) {
            return null;
        }
        return uuid(config.path("datasetId").asText(null));
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private ApiException unresolved(DraftNode node, String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INPUT_VERSION_UNRESOLVED",
                message,
                Map.of("nodeId", node.id().toString())
        );
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
