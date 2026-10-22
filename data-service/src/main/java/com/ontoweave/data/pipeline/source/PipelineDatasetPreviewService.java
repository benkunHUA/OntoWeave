package com.ontoweave.data.pipeline.source;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.service.PipelineWriteGuard;
import com.ontoweave.data.sync.api.dto.DatasetPreviewResponse;
import com.ontoweave.data.sync.api.dto.PreviewColumnResponse;
import com.ontoweave.data.sync.iceberg.IcebergPreview;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.ontoweave.data.pipeline.service.PipelineErrors.notFound;

@Service
public class PipelineDatasetPreviewService {

    public static final int MAX_LIMIT = 500;

    private final PipelineWriteGuard pipelines;
    private final PipelineDatasetRepository datasets;
    private final PipelineDatasetVersionRepository versions;
    private final IcebergPreviewReader reader;

    public PipelineDatasetPreviewService(
            PipelineWriteGuard pipelines,
            PipelineDatasetRepository datasets,
            PipelineDatasetVersionRepository versions,
            IcebergPreviewReader reader
    ) {
        this.pipelines = pipelines;
        this.datasets = datasets;
        this.versions = versions;
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public DatasetPreviewResponse preview(
            UUID pipelineId,
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
        pipelines.requireReadable(pipelineId, ownerId);
        PipelineDatasetEntity dataset = datasets.findByIdAndPipelineId(datasetId, pipelineId)
                .orElseThrow(() -> notFound("输出数据集不存在"));
        UUID selectedVersionId = versionId == null ? dataset.getCurrentVersionId() : versionId;
        if (selectedVersionId == null) {
            throw notFound("数据集版本不存在或未发布");
        }
        PipelineDatasetVersionEntity version = versions
                .findByIdAndDatasetId(selectedVersionId, datasetId)
                .filter(item -> item.getPublishedAt() != null)
                .orElseThrow(() -> notFound("数据集版本不存在或未发布"));
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
}
