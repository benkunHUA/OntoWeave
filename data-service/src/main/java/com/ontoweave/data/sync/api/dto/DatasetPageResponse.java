package com.ontoweave.data.sync.api.dto;

import java.util.List;

public record DatasetPageResponse(
        List<DatasetListItemResponse> items,
        long total,
        int limit,
        int offset
) {
}
