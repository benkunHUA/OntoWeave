package com.ontoweave.data.sync.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DatasetPreviewResponse(
        UUID datasetId,
        UUID versionId,
        int versionNumber,
        List<String> columns,
        List<Map<String, Object>> rows,
        boolean truncated
) {
}
