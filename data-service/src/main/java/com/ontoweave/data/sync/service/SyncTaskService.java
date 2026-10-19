package com.ontoweave.data.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.api.dto.TableDetailResponse;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.service.ConnectionMaterialService;
import com.ontoweave.data.connection.service.DataConnectionService;
import com.ontoweave.data.connector.mysql.MysqlTableDiscovery;
import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.sync.api.dto.CreateSyncTasksRequest;
import com.ontoweave.data.sync.api.dto.SyncTaskResponse;
import com.ontoweave.data.sync.api.dto.SyncTasksResponse;
import com.ontoweave.data.sync.api.dto.TableSelectionRequest;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SyncTaskService {

    private final DataConnectionService connections;
    private final ConnectionMaterialService materials;
    private final MysqlTableDiscovery discovery;
    private final MysqlCdcPreflight cdcPreflight;
    private final SyncTaskRepository tasks;
    private final SyncRunRepository runs;
    private final DatasetRepository datasets;
    private final SyncProperties properties;
    private final ObjectMapper objectMapper;
    private final OutboxWriter outbox;

    public SyncTaskService(
            DataConnectionService connections,
            ConnectionMaterialService materials,
            MysqlTableDiscovery discovery,
            MysqlCdcPreflight cdcPreflight,
            SyncTaskRepository tasks,
            SyncRunRepository runs,
            DatasetRepository datasets,
            SyncProperties properties,
            ObjectMapper objectMapper,
            OutboxWriter outbox
    ) {
        this.connections = connections;
        this.materials = materials;
        this.discovery = discovery;
        this.cdcPreflight = cdcPreflight;
        this.tasks = tasks;
        this.runs = runs;
        this.datasets = datasets;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.outbox = outbox;
    }

    @Transactional
    public SyncTasksResponse create(
            UUID connectionId,
            UUID ownerId,
            CreateSyncTasksRequest request,
            UUID operationId
    ) {
        DataConnectionEntity connection = connections.required(connectionId, ownerId);
        ConnectionMaterialService.MysqlConnectionMaterial material = materials.loadMysql(connectionId, ownerId);
        List<TableSelectionRequest> selections = deduplicate(request.tables());
        List<Map<String, Object>> errors = new ArrayList<>();
        Map<String, TableDetailResponse> details = new LinkedHashMap<>();
        for (TableSelectionRequest selection : selections) {
            try {
                TableDetailResponse detail = discovery.describe(
                        material.config(),
                        material.password(),
                        selection.schema(),
                        selection.table()
                );
                if (detail.primaryKeyColumns().isEmpty()) {
                    errors.add(tableError(selection, "TABLE_WITHOUT_PRIMARY_KEY", "表没有主键，不能同步"));
                    continue;
                }
                details.put(key(selection.schema(), selection.table()), detail);
            } catch (ApiException exception) {
                errors.add(tableError(selection, exception.code(), exception.getMessage()));
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TABLE_VALIDATION_FAILED",
                    "部分表不能同步",
                    Map.of("tables", errors)
            );
        }

        if (selections.stream().anyMatch(item -> SyncMode.from(item.syncMode()) == SyncMode.CDC)) {
            cdcPreflight.validate(material);
        }

        List<SyncTaskResponse> result = new ArrayList<>();
        Set<String> selectedKeys = new LinkedHashSet<>();
        for (TableSelectionRequest selection : selections) {
            selectedKeys.add(key(selection.schema(), selection.table()));
            int interval = interval(selection.versionIntervalSeconds());
            SyncMode mode = SyncMode.from(selection.syncMode());
            TableDetailResponse detail = details.get(key(selection.schema(), selection.table()));
            SyncTaskEntity task = tasks
                    .findByConnectionIdAndSchemaNameAndTableName(
                            connectionId,
                            selection.schema(),
                            selection.table()
                    )
                    .orElse(null);
            if (task == null) {
                UUID datasetId = UUID.randomUUID();
                UUID taskId = UUID.randomUUID();
                DatasetEntity dataset = new DatasetEntity(
                        datasetId,
                        connectionId,
                        selection.schema() + "." + selection.table()
                );
                dataset = datasets.save(dataset);
                task = new SyncTaskEntity(
                        taskId,
                        connectionId,
                        datasetId,
                        selection.schema(),
                        selection.table(),
                        mode,
                        interval,
                        objectMapper.valueToTree(detail.primaryKeyColumns())
                );
                task = tasks.save(task);
                dataset.bindTask(taskId);
                datasets.save(dataset);
            } else {
                task.update(mode, interval, objectMapper.valueToTree(detail.primaryKeyColumns()));
                task = tasks.save(task);
            }
            result.add(response(task));
        }
        for (SyncTaskEntity existing : tasks.findByConnectionIdOrderByCreatedAtAsc(connectionId)) {
            if (!selectedKeys.contains(key(existing.getSchemaName(), existing.getTableName()))) {
                Optional<SyncRunEntity> active = runs
                        .findFirstBySyncTaskIdAndStatusInOrderByCreatedAtDesc(
                                existing.getId(),
                                SyncRunStatus.ACTIVE
                        );
                if (active.isPresent()) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "SYNC_RUN_ACTIVE",
                            "%s.%s 正在同步中，请先停止当前运行后再取消选择".formatted(
                                    existing.getSchemaName(),
                                    existing.getTableName()
                            ),
                            Map.of(
                                    "syncTaskId", existing.getId(),
                                    "runId", active.get().getId(),
                                    "status", active.get().getStatus().value()
                            )
                    );
                }
                existing.disable();
                tasks.save(existing);
            }
        }
        writeSavedEvent(connection, operationId, result);
        return new SyncTasksResponse(result);
    }

    private void writeSavedEvent(
            DataConnectionEntity connection,
            UUID operationId,
            List<SyncTaskResponse> tasks
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("connectionId", connection.getId());
        payload.put("conversationId", connection.getConversationId());
        payload.put("operationId", operationId);
        payload.put("taskIds", tasks.stream().map(SyncTaskResponse::id).toList());
        payload.put("summary", "已保存 " + tasks.size() + " 张表的同步配置");
        outbox.write("data_connection", connection.getId(), "sync.tasks.saved", payload);
    }

    @Transactional(readOnly = true)
    public SyncTasksResponse list(UUID connectionId, UUID ownerId) {
        connections.required(connectionId, ownerId);
        return new SyncTasksResponse(
                tasks.findByConnectionIdOrderByCreatedAtAsc(connectionId).stream()
                        .map(this::response)
                        .toList()
        );
    }

    public SyncTaskResponse response(SyncTaskEntity task) {
        return new SyncTaskResponse(
                task.getId(),
                task.getConnectionId(),
                task.getDatasetId(),
                task.getSchemaName(),
                task.getTableName(),
                task.getSyncMode().value(),
                task.getVersionIntervalSeconds(),
                stringList(task.getPrimaryKeyColumns()),
                task.getStatus().value(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    public List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> values.add(value.asText()));
        }
        return values;
    }

    private int interval(Integer requested) {
        int interval = requested == null ? properties.defaultVersionIntervalSeconds() : requested;
        if (interval < properties.checkpointIntervalSeconds()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "versionIntervalSeconds 不能短于 checkpoint 间隔"
            );
        }
        return interval;
    }

    private List<TableSelectionRequest> deduplicate(List<TableSelectionRequest> input) {
        Map<String, TableSelectionRequest> unique = new LinkedHashMap<>();
        for (TableSelectionRequest item : input) {
            unique.putIfAbsent(key(item.schema(), item.table()), item);
        }
        return List.copyOf(unique.values());
    }

    private Map<String, Object> tableError(TableSelectionRequest selection, String code, String message) {
        return Map.of(
                "schema", selection.schema(),
                "table", selection.table(),
                "code", code,
                "message", message
        );
    }

    private String key(String schema, String table) {
        return schema + "\u0000" + table;
    }
}
