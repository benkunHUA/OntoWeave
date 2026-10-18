package com.ontoweave.data.sync.service;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.sync.api.dto.DatasetPreviewResponse;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DatasetPreviewService {

    public static final int MAX_LIMIT = 500;

    private final DatasetService datasets;
    private final DatasetVersionRepository versions;
    private final IcebergPreviewReader reader;

    public DatasetPreviewService(
            DatasetService datasets,
            DatasetVersionRepository versions,
            IcebergPreviewReader reader
    ) {
        this.datasets = datasets;
        this.versions = versions;
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public DatasetPreviewResponse preview(
            UUID datasetId,
            UUID versionId,
            UUID ownerId,
            int limit
    ) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LIMIT",
                    "limit 必须在 1 到 " + MAX_LIMIT + " 之间"
            );
        }
        DatasetEntity dataset = datasets.required(datasetId, ownerId);
        DatasetVersionEntity version = resolveVersion(dataset, versionId);
        IcebergPreview preview = reader.read(
                version.getIcebergCatalog(),
                version.getIcebergNamespace(),
                version.getIcebergTable(),
                version.getIcebergSnapshotId(),
                limit
        ).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "DATASET_VERSION_UNAVAILABLE",
                "版本对应的数据文件不存在或已被清理"
        ));
        return new DatasetPreviewResponse(
                dataset.getId(),
                version.getId(),
                version.getVersionNumber(),
                preview.columns(),
                preview.rows(),
                preview.truncated()
        );
    }

    private DatasetVersionEntity resolveVersion(DatasetEntity dataset, UUID versionId) {
        if (versionId != null) {
            return versions.findByIdAndDatasetId(versionId, dataset.getId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "NOT_FOUND",
                            "数据集版本不存在"
                    ));
        }
        if (dataset.getCurrentVersionId() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DATASET_NOT_READY",
                    "数据集还没有已发布的版本"
            );
        }
        return versions.findByIdAndDatasetId(dataset.getCurrentVersionId(), dataset.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "数据集当前版本不存在"
                ));
    }
}
