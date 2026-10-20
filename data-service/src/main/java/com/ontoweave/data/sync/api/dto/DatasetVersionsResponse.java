package com.ontoweave.data.sync.api.dto;

import java.util.List;
import java.util.UUID;

public record DatasetVersionsResponse(
        UUID datasetId,
        int total,
        int limit,
        int offset,
        List<DatasetVersionResponse> versions
) {
}
