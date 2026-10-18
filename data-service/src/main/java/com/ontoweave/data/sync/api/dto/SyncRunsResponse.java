package com.ontoweave.data.sync.api.dto;

import java.util.List;
import java.util.UUID;

public record SyncRunsResponse(UUID operationId, List<SyncRunResponse> runs) {
}
