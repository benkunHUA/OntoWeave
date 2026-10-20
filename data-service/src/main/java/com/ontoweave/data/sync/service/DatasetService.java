package com.ontoweave.data.sync.service;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.service.DataConnectionService;
import com.ontoweave.data.sync.api.dto.DatasetResponse;
import com.ontoweave.data.sync.api.dto.DatasetVersionResponse;
import com.ontoweave.data.sync.api.dto.DatasetVersionsResponse;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DatasetService {

    private final DataConnectionService connections;
    private final DatasetRepository datasets;
    private final DatasetVersionRepository versions;
    private final SyncTaskRepository tasks;

    public DatasetService(
            DataConnectionService connections,
            DatasetRepository datasets,
            DatasetVersionRepository versions,
            SyncTaskRepository tasks
    ) {
        this.connections = connections;
        this.datasets = datasets;
        this.versions = versions;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(UUID datasetId, UUID ownerId) {
        DatasetEntity dataset = required(datasetId, ownerId);
        List<DatasetVersionEntity> all = versions.findByDatasetIdOrderByVersionNumberDesc(datasetId);
        DatasetVersionEntity current = dataset.getCurrentVersionId() == null
                ? null
                : versions.findByIdAndDatasetId(dataset.getCurrentVersionId(), datasetId).orElse(null);
        var task = dataset.getSyncTaskId() == null
                ? null
                : tasks.findById(dataset.getSyncTaskId()).orElse(null);
        return new DatasetResponse(
                dataset.getId(),
                dataset.getConnectionId(),
                dataset.getSyncTaskId(),
                dataset.getName(),
                dataset.getActiveIcebergCatalog(),
                dataset.getActiveIcebergNamespace(),
                dataset.getActiveIcebergTable(),
                dataset.getCurrentVersionId(),
                current == null ? null : version(current),
                all.stream().map(this::version).toList(),
                all.size(),
                task == null ? null : task.getSchemaName(),
                task == null ? null : task.getTableName(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public DatasetVersionsResponse versions(UUID datasetId, UUID ownerId, int limit, int offset) {
        if (limit < 1 || limit > 200 || offset < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION",
                    "limit 必须在 1 到 200 之间，offset 不能为负数"
            );
        }
        required(datasetId, ownerId);
        List<DatasetVersionEntity> page = versions.findPageByDatasetId(datasetId, limit, offset);
        return new DatasetVersionsResponse(
                datasetId,
                Math.toIntExact(versions.countByDatasetId(datasetId)),
                limit,
                offset,
                page.stream().map(this::version).toList()
        );
    }

    @Transactional(readOnly = true)
    public DatasetVersionResponse version(UUID datasetId, UUID versionId, UUID ownerId) {
        required(datasetId, ownerId);
        return versions.findByIdAndDatasetId(versionId, datasetId)
                .map(this::version)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "数据集版本不存在"
                ));
    }

    @Transactional(readOnly = true)
    public List<DatasetEntity> list(UUID connectionId, UUID ownerId) {
        connections.required(connectionId, ownerId);
        return datasets.findByConnectionIdOrderByCreatedAtAsc(connectionId);
    }

    public DatasetEntity required(UUID datasetId, UUID ownerId) {
        DatasetEntity dataset = datasets.findById(datasetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "数据集不存在"));
        connections.required(dataset.getConnectionId(), ownerId);
        return dataset;
    }

    public DatasetVersionResponse version(DatasetVersionEntity version) {
        return new DatasetVersionResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getIcebergCatalog(),
                version.getIcebergNamespace(),
                version.getIcebergTable(),
                version.getIcebergSnapshotId(),
                version.getRowCount(),
                version.isRowCountEstimated(),
                version.getByteSize(),
                version.getSchemaJson(),
                version.getColumnCount(),
                version.getCommittedAt(),
                version.getPublishedAt(),
                version.getVersionSource().value(),
                version.getSourceRunId(),
                version.getStoragePath()
        );
    }
}
