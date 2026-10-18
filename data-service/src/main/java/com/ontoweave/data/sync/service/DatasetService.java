package com.ontoweave.data.sync.service;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.service.DataConnectionService;
import com.ontoweave.data.sync.api.dto.DatasetResponse;
import com.ontoweave.data.sync.api.dto.DatasetVersionResponse;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
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

    public DatasetService(
            DataConnectionService connections,
            DatasetRepository datasets,
            DatasetVersionRepository versions
    ) {
        this.connections = connections;
        this.datasets = datasets;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(UUID datasetId, UUID ownerId) {
        DatasetEntity dataset = required(datasetId, ownerId);
        List<DatasetVersionEntity> all = versions.findByDatasetIdOrderByVersionNumberDesc(datasetId);
        DatasetVersionEntity current = dataset.getCurrentVersionId() == null
                ? null
                : versions.findByIdAndDatasetId(dataset.getCurrentVersionId(), datasetId).orElse(null);
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
                dataset.getCreatedAt(),
                dataset.getUpdatedAt()
        );
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
                version.getVersionSource().value()
        );
    }
}
