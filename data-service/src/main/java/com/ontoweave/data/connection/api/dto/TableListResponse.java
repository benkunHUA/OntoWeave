package com.ontoweave.data.connection.api.dto;

import java.util.List;

public record TableListResponse(
        List<TableSummaryResponse> tables,
        List<String> warnings
) {
}
