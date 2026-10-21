package com.ontoweave.data.pipeline.source;

import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DatasetMetadataQuery {

    static final String KIND_SYNC = "sync";
    static final String KIND_PIPELINE = "pipeline";

    private final DatasetRepository datasets;
    private final DatasetVersionRepository datasetVersions;
    private final SyncTaskRepository syncTasks;
    private final DataConnectionRepository connections;
    private final PipelineRepository pipelines;
    private final PipelineDatasetRepository pipelineDatasets;
    private final PipelineDatasetVersionRepository pipelineDatasetVersions;

    public DatasetMetadataQuery(
            DatasetRepository datasets,
            DatasetVersionRepository datasetVersions,
            SyncTaskRepository syncTasks,
            DataConnectionRepository connections,
            PipelineRepository pipelines,
            PipelineDatasetRepository pipelineDatasets,
            PipelineDatasetVersionRepository pipelineDatasetVersions
    ) {
        this.datasets = datasets;
        this.datasetVersions = datasetVersions;
        this.syncTasks = syncTasks;
        this.connections = connections;
        this.pipelines = pipelines;
        this.pipelineDatasets = pipelineDatasets;
        this.pipelineDatasetVersions = pipelineDatasetVersions;
    }

    @Transactional(readOnly = true)
    public Map<DatasetRef, DatasetMetadata> batchLoad(UUID ownerId, List<DatasetRef> refs) {
        LinkedHashSet<DatasetRef> unique = new LinkedHashSet<>();
        if (refs != null) {
            refs.stream().filter(Objects::nonNull).forEach(unique::add);
        }
        if (unique.isEmpty()) {
            return Map.of();
        }
        Map<DatasetRef, DatasetMetadata> result = new LinkedHashMap<>();
        List<DatasetRef> syncRefs = unique.stream().filter(this::sync).toList();
        List<DatasetRef> pipelineRefs = unique.stream().filter(this::pipeline).toList();
        if (!syncRefs.isEmpty()) {
            result.putAll(loadSync(syncRefs, ownerId));
        }
        if (!pipelineRefs.isEmpty()) {
            result.putAll(loadPipeline(pipelineRefs, ownerId));
        }
        unique.stream().filter(ref -> !result.containsKey(ref))
                .forEach(ref -> result.put(ref, DatasetMetadata.missing()));
        return result;
    }

    private Map<DatasetRef, DatasetMetadata> loadSync(List<DatasetRef> refs, UUID ownerId) {
        List<UUID> ids = refs.stream().map(DatasetRef::datasetId).toList();
        Map<UUID, DatasetEntity> datasetById = datasets.findByIdIn(ids).stream()
                .collect(Collectors.toMap(DatasetEntity::getId, Function.identity()));
        Collection<UUID> versionIds = datasetById.values().stream()
                .map(DatasetEntity::getCurrentVersionId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, DatasetVersionEntity> versionById = versionIds.isEmpty()
                ? Map.of()
                : datasetVersions.findByIdIn(versionIds).stream()
                        .collect(Collectors.toMap(DatasetVersionEntity::getId, Function.identity()));
        Map<UUID, SyncTaskEntity> taskByDataset = syncTasks.findByDatasetIdIn(ids).stream()
                .collect(Collectors.toMap(SyncTaskEntity::getDatasetId, Function.identity()));
        Collection<UUID> connectionIds = datasetById.values().stream()
                .map(DatasetEntity::getConnectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, DataConnectionEntity> visibleConnections = connectionIds.isEmpty()
                ? Map.of()
                : connections.findByIdInAndOwnerId(connectionIds, ownerId).stream()
                        .collect(Collectors.toMap(DataConnectionEntity::getId, Function.identity()));

        Map<DatasetRef, DatasetMetadata> result = new LinkedHashMap<>();
        for (DatasetRef ref : refs) {
            DatasetEntity dataset = datasetById.get(ref.datasetId());
            if (dataset == null) {
                result.put(ref, DatasetMetadata.missing());
                continue;
            }
            DataConnectionEntity connection = visibleConnections.get(dataset.getConnectionId());
            if (connection == null) {
                result.put(ref, DatasetMetadata.restricted());
                continue;
            }
            SyncTaskEntity task = taskByDataset.get(dataset.getId());
            if (task == null) {
                result.put(ref, DatasetMetadata.missing());
                continue;
            }
            DatasetVersionEntity version = dataset.getCurrentVersionId() == null
                    ? null
                    : versionById.get(dataset.getCurrentVersionId());
            result.put(ref, new DatasetMetadata(
                    dataset.getName(),
                    version == null ? null : version.getVersionNumber(),
                    connection.getId(),
                    connection.getName(),
                    task.getSchemaName(),
                    task.getTableName(),
                    MetadataStatus.AVAILABLE
            ));
        }
        return result;
    }

    private Map<DatasetRef, DatasetMetadata> loadPipeline(List<DatasetRef> refs, UUID ownerId) {
        List<UUID> ids = refs.stream().map(DatasetRef::datasetId).toList();
        Map<UUID, PipelineDatasetEntity> datasetById = pipelineDatasets.findByIdIn(ids).stream()
                .collect(Collectors.toMap(PipelineDatasetEntity::getId, Function.identity()));
        Collection<UUID> pipelineIds = datasetById.values().stream()
                .map(PipelineDatasetEntity::getPipelineId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> visiblePipelineIds = pipelineIds.isEmpty()
                ? Set.of()
                : pipelines.findByIdInAndOwnerId(pipelineIds, ownerId).stream()
                        .map(PipelineEntity::getId)
                        .collect(Collectors.toSet());
        Collection<UUID> versionIds = datasetById.values().stream()
                .map(PipelineDatasetEntity::getCurrentVersionId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, PipelineDatasetVersionEntity> versionById = versionIds.isEmpty()
                ? Map.of()
                : pipelineDatasetVersions.findByIdIn(versionIds).stream()
                        .collect(Collectors.toMap(PipelineDatasetVersionEntity::getId, Function.identity()));
        Map<DatasetRef, DatasetMetadata> result = new LinkedHashMap<>();
        for (DatasetRef ref : refs) {
            PipelineDatasetEntity dataset = datasetById.get(ref.datasetId());
            if (dataset == null) {
                result.put(ref, DatasetMetadata.missing());
                continue;
            }
            if (!visiblePipelineIds.contains(dataset.getPipelineId())) {
                result.put(ref, DatasetMetadata.restricted());
                continue;
            }
            PipelineDatasetVersionEntity version = dataset.getCurrentVersionId() == null
                    ? null
                    : versionById.get(dataset.getCurrentVersionId());
            result.put(ref, new DatasetMetadata(
                    dataset.getName(),
                    version == null ? null : version.getVersionNumber(),
                    null,
                    null,
                    null,
                    null,
                    MetadataStatus.AVAILABLE
            ));
        }
        return result;
    }

    private boolean sync(DatasetRef ref) {
        return KIND_SYNC.equals(ref.datasetKind());
    }

    private boolean pipeline(DatasetRef ref) {
        return KIND_PIPELINE.equals(ref.datasetKind());
    }
}
