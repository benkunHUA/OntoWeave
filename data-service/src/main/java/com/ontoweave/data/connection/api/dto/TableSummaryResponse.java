package com.ontoweave.data.connection.api.dto;

public record TableSummaryResponse(
        String schema,
        String name,
        String type,
        String comment,
        Long estimatedRows,
        boolean hasPrimaryKey
) {
}
