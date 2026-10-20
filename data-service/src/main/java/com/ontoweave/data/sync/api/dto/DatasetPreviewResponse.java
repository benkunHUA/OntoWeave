package com.ontoweave.data.sync.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DatasetPreviewResponse(
        UUID datasetId,
        UUID versionId,
        Integer versionNumber,
        long snapshotId,
        boolean live,
        List<PreviewColumnResponse> columns,
        long rowCount,
        List<Map<String, Object>> rows,
        boolean truncated
) {
}
