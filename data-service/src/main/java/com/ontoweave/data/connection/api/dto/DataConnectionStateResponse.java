package com.ontoweave.data.connection.api.dto;

import com.ontoweave.data.sync.api.dto.DatasetResponse;
import com.ontoweave.data.sync.api.dto.SyncRunResponse;
import com.ontoweave.data.sync.api.dto.SyncTaskResponse;

import java.util.List;
import java.util.UUID;

public record DataConnectionStateResponse(
        DataConnectionResponse connection,
        List<SyncTaskResponse> tasks,
        List<SyncRunResponse> recentRuns,
        List<DatasetResponse> datasets,
        List<String> missingSteps,
        UUID lastEventId
) {
}
