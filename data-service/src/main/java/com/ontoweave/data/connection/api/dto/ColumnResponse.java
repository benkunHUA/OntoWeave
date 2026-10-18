package com.ontoweave.data.connection.api.dto;

public record ColumnResponse(
        String name,
        int ordinalPosition,
        String dataType,
        String columnType,
        boolean nullable,
        String defaultValue,
        boolean primaryKey,
        String comment
) {
}
