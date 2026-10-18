package com.ontoweave.data.connection.api.dto;

import java.util.List;

public record DataConnectionPageResponse(
        List<DataConnectionResponse> items,
        long total,
        int limit,
        int offset
) {
}
