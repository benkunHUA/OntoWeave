package com.ontoweave.data.connection.api.dto;

import java.util.List;

public record TableDetailResponse(
        String schema,
        String table,
        String type,
        String comment,
        List<ColumnResponse> columns,
        List<String> primaryKeyColumns
) {
}
