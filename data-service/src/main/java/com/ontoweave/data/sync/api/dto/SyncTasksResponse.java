package com.ontoweave.data.sync.api.dto;

import java.util.List;

public record SyncTasksResponse(List<SyncTaskResponse> tasks) {
}
