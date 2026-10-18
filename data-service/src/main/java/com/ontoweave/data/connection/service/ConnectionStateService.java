package com.ontoweave.data.connection.service;

import com.ontoweave.data.connection.api.dto.DataConnectionResponse;
import com.ontoweave.data.connection.api.dto.DataConnectionStateResponse;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.domain.TestStatus;
import com.ontoweave.data.outbox.OutboxEventRepository;
import com.ontoweave.data.sync.api.dto.DatasetResponse;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.service.DatasetService;
import com.ontoweave.data.sync.service.SyncRunService;
import com.ontoweave.data.sync.service.SyncTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectionStateService {

    private final DataConnectionService connections;
    private final OutboxEventRepository outbox;
    private final SyncTaskService syncTasks;
    private final SyncRunService syncRuns;
    private final DatasetService datasets;

    public ConnectionStateService(
            DataConnectionService connections,
            OutboxEventRepository outbox,
            SyncTaskService syncTasks,
            SyncRunService syncRuns,
            DatasetService datasets
    ) {
        this.connections = connections;
        this.outbox = outbox;
        this.syncTasks = syncTasks;
        this.syncRuns = syncRuns;
        this.datasets = datasets;
    }

    @Transactional(readOnly = true)
    public DataConnectionStateResponse state(UUID id, UUID ownerId) {
        DataConnectionEntity connection = connections.required(id, ownerId);
        DataConnectionResponse response = connections.response(connection);
        List<String> missing = new ArrayList<>();
        if (connection.getConfig() == null || connection.getConfig().isEmpty() || !response.secretConfigured()) {
            missing.add("SAVE_CONNECTION");
        }
        if (connection.getLastTestStatus() != TestStatus.SUCCESS) {
            missing.add("TEST_CONNECTION");
        }
        List<com.ontoweave.data.sync.api.dto.SyncTaskResponse> taskResponses = syncTasks.list(id, ownerId).tasks();
        if (taskResponses.isEmpty()) {
            missing.add("SELECT_TABLES");
            missing.add("CREATE_SYNC");
        }
        List<SyncRunEntity> recentRunEntities = syncRuns.recent(id, ownerId);
        List<com.ontoweave.data.sync.api.dto.SyncRunResponse> recentRuns = recentRunEntities.stream()
                .map(syncRuns::response)
                .toList();
        if (recentRunEntities.isEmpty()) {
            missing.add("RUN_SYNC");
        }
        List<DatasetResponse> datasetResponses = datasets.list(id, ownerId).stream()
                .map(DatasetEntity::getId)
                .map(datasetId -> datasets.get(datasetId, ownerId))
                .toList();
        UUID lastEventId = outbox
                .findTopByAggregateTypeAndAggregateIdOrderByCreatedAtDesc("data_connection", id)
                .map(event -> event.getId())
                .orElse(null);
        return new DataConnectionStateResponse(
                response,
                taskResponses,
                recentRuns,
                datasetResponses,
                List.copyOf(missing),
                lastEventId
        );
    }
}
