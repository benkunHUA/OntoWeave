package com.ontoweave.data.sync.service;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.sync.api.dto.DatasetPreviewResponse;
import com.ontoweave.data.sync.api.dto.PreviewColumnResponse;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DatasetPreviewService {

    public static final int MAX_LIMIT = 500;

    private final DatasetService datasets;
    private final DatasetVersionRepository versions;
    private final SyncRunRepository runs;
    private final IcebergCatalogClient iceberg;
    private final IcebergPreviewReader reader;

    public DatasetPreviewService(
            DatasetService datasets,
            DatasetVersionRepository versions,
            SyncRunRepository runs,
            IcebergCatalogClient iceberg,
            IcebergPreviewReader reader
    ) {
        this.datasets = datasets;
        this.versions = versions;
        this.runs = runs;
        this.iceberg = iceberg;
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public DatasetPreviewResponse preview(
            UUID datasetId,
            UUID versionId,
            UUID ownerId,
            int limit,
            boolean live
    ) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LIMIT",
                    "limit 必须在 1 到 " + MAX_LIMIT + " 之间"
            );
        }
        DatasetEntity dataset = datasets.required(datasetId, ownerId);
        if (live && versionId != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PREVIEW",
                    "实时预览不能指定 version_id"
            );
        }
        if (live) {
            return livePreview(dataset, limit);
        }
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
                version.getIcebergSnapshotId(),
                false,
                preview.columns().stream()
                        .map(column -> new PreviewColumnResponse(
                                column.name(), column.type(), column.nullable()
                        ))
                        .toList(),
                version.getRowCount(),
                preview.rows(),
                preview.truncated()
        );
    }

    private DatasetPreviewResponse livePreview(DatasetEntity dataset, int limit) {
        for (SyncRunEntity run : runs.findByDatasetIdOrderByCreatedAtDesc(dataset.getId())) {
            if (run.getStatus() == SyncRunStatus.FAILED
                    || run.getIcebergCatalog() == null
                    || run.getIcebergNamespace() == null
                    || run.getIcebergTable() == null) {
                continue;
            }
            Optional<IcebergSnapshotInfo> snapshot = iceberg.latestSnapshot(
                    run.getIcebergCatalog(),
                    run.getIcebergNamespace(),
                    run.getIcebergTable()
            );
            if (snapshot.isEmpty()) {
                continue;
            }
            Optional<IcebergPreview> preview = reader.read(
                    run.getIcebergCatalog(),
                    run.getIcebergNamespace(),
                    run.getIcebergTable(),
                    snapshot.get().snapshotId(),
                    limit
            );
            if (preview.isEmpty()) {
                continue;
            }
            return new DatasetPreviewResponse(
                    dataset.getId(),
                    null,
                    null,
                    snapshot.get().snapshotId(),
                    true,
                    preview.get().columns().stream()
                            .map(column -> new PreviewColumnResponse(
                                    column.name(), column.type(), column.nullable()
                            ))
                            .toList(),
                    snapshot.get().rowCount(),
                    preview.get().rows(),
                    preview.get().truncated()
            );
        }
        throw new ApiException(
                HttpStatus.NOT_FOUND,
                "DATASET_LIVE_PREVIEW_UNAVAILABLE",
                "还没有可预览的同步数据"
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
