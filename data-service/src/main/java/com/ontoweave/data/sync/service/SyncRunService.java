package com.ontoweave.data.sync.service;

import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.service.DataConnectionService;
import com.ontoweave.data.sync.api.dto.SyncRunResponse;
import com.ontoweave.data.sync.api.dto.SyncRunsResponse;
import com.ontoweave.data.sync.api.dto.TableRef;
import com.ontoweave.data.sync.api.dto.TriggerSyncRequest;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.execution.SyncRunEventWriter;
import com.ontoweave.data.sync.execution.SyncRunStopInitiator;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SyncRunService {

    private static final Logger log = LoggerFactory.getLogger(SyncRunService.class);
    private final DataConnectionService connections;
    private final SyncTaskRepository tasks;
    private final SyncRunRepository runs;
    private final SyncRunEventWriter events;
    private final SyncProperties properties;
    private final SyncRunStopInitiator stopInitiator;
    private final TransactionTemplate transactions;

    public SyncRunService(
            DataConnectionService connections,
            SyncTaskRepository tasks,
            SyncRunRepository runs,
            SyncRunEventWriter events,
            SyncProperties properties,
            SyncRunStopInitiator stopInitiator,
            PlatformTransactionManager transactionManager
    ) {
        this.connections = connections;
        this.tasks = tasks;
        this.runs = runs;
        this.events = events;
        this.properties = properties;
        this.stopInitiator = stopInitiator;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public SyncRunsResponse trigger(UUID connectionId, UUID ownerId, TriggerSyncRequest request) {
        connections.required(connectionId, ownerId);
        List<SyncTaskEntity> selected = selectTasks(connectionId, request.tables());
        if (selected.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_SYNC_TASKS", "连接下没有可运行的同步任务");
        }
        List<SyncRunEntity> result = new ArrayList<>();
        for (SyncTaskEntity task : selected) {
            Optional<SyncRunEntity> sameOperation = runs
                    .findBySyncTaskIdAndOperationId(task.getId(), request.operationId());
            if (sameOperation.isPresent()) {
                result.add(sameOperation.get());
                continue;
            }
            Optional<SyncRunEntity> active = runs
                    .findFirstBySyncTaskIdAndStatusInOrderByCreatedAtDesc(
                            task.getId(),
                            SyncRunStatus.ACTIVE
                    );
            if (active.isPresent()) {
                SyncRunEntity run = active.get();
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "SYNC_RUN_ACTIVE",
                        "%s.%s 正在同步中（%s），请先停止当前运行再重新开始".formatted(
                                task.getSchemaName(),
                                task.getTableName(),
                                run.getStatus().value()
                        ),
                        Map.of(
                                "syncTaskId", task.getId(),
                                "runId", run.getId(),
                                "status", run.getStatus().value()
                        )
                );
            }
            result.add(createRun(connectionId, task, request.operationId()));
        }
        return new SyncRunsResponse(request.operationId(), result.stream().map(this::response).toList());
    }

    public SyncRunResponse stop(UUID runId, UUID ownerId) {
        SyncRunEntity run = required(runId, ownerId);
        if (run.getStatus().terminal()) {
            return response(run);
        }

        Boolean transitioned = transactions.execute(status -> {
            int updated = runs.requestStop(
                    runId,
                    SyncRunStatus.ACTIVE,
                    SyncRunStatus.STOPPING,
                    Instant.now()
            );
            if (updated != 1) {
                return false;
            }
            SyncRunEntity stopping = runs.findById(runId).orElse(run);
            tasks.findById(stopping.getSyncTaskId()).ifPresent(task ->
                    events.write(stopping, task, "sync.run.stopping", "已请求停止")
            );
            return true;
        });

        SyncRunEntity current = runs.findById(runId).orElse(run);
        if (Boolean.TRUE.equals(transitioned)) {
            try {
                stopInitiator.initiate(current);
            } catch (RuntimeException exception) {
                log.warn("failed to initiate Flink stop for run {}", runId, exception);
            }
            current = runs.findById(runId).orElse(current);
        }
        return response(current);
    }

    @Transactional(readOnly = true)
    public SyncRunResponse get(UUID runId, UUID ownerId) {
        return response(required(runId, ownerId));
    }

    @Transactional(readOnly = true)
    public List<SyncRunEntity> recent(UUID connectionId, UUID ownerId) {
        connections.required(connectionId, ownerId);
        List<SyncRunEntity> all = runs.findRecentByConnectionId(connectionId);
        return all.size() > 20 ? all.subList(0, 20) : all;
    }

    public SyncRunEntity required(UUID runId, UUID ownerId) {
        SyncRunEntity run = runs.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "同步运行不存在"));
        SyncTaskEntity task = tasks.findById(run.getSyncTaskId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "同步任务不存在"));
        connections.required(task.getConnectionId(), ownerId);
        return run;
    }

    public SyncRunResponse response(SyncRunEntity run) {
        return new SyncRunResponse(
                run.getId(),
                run.getSyncTaskId(),
                run.getDatasetId(),
                run.getOperationId(),
                run.getRunMode().value(),
                run.getStatus().value(),
                run.getFlinkJobId(),
                run.getFlinkJobName(),
                run.getFlinkJobState(),
                run.getIcebergCatalog(),
                run.getIcebergNamespace(),
                run.getIcebergTable(),
                run.getServerIdStart(),
                run.getServerIdEnd(),
                run.getSnapshotCompletedAt(),
                run.getLastCheckpointAt(),
                run.getLastSnapshotId(),
                run.getLastPublishedSnapshotId(),
                run.getVersionIntervalSeconds(),
                run.getNextVersionDueAt(),
                run.getSavepointPath(),
                run.getErrorCode(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt()
        );
    }

    private SyncRunEntity createRun(UUID connectionId, SyncTaskEntity task, UUID operationId) {
        UUID runId = UUID.randomUUID();
        String namespace = "ontoweave_conn_" + shortId(connectionId);
        String table = "ds_" + shortId(task.getDatasetId()) + "_r" + shortId(runId);
        SyncRunEntity run = new SyncRunEntity(
                runId,
                task.getId(),
                task.getDatasetId(),
                operationId,
                task.getSyncMode(),
                "ontoweave-run-" + runId,
                properties.icebergCatalog(),
                namespace,
                table,
                task.getVersionIntervalSeconds()
        );
        run = runs.save(run);
        events.write(run, task, "sync.run.queued", "已进入同步队列");
        return run;
    }

    private List<SyncTaskEntity> selectTasks(UUID connectionId, List<TableRef> selected) {
        if (selected == null || selected.isEmpty()) {
            return tasks.findByConnectionIdAndStatusOrderByCreatedAtAsc(
                    connectionId,
                    com.ontoweave.data.sync.domain.SyncTaskStatus.ENABLED
            );
        }
        Map<String, TableRef> wanted = new LinkedHashMap<>();
        selected.forEach(item -> wanted.put(item.schema() + "\u0000" + item.table(), item));
        List<SyncTaskEntity> result = new ArrayList<>();
        for (SyncTaskEntity task : tasks.findByConnectionIdOrderByCreatedAtAsc(connectionId)) {
            if (wanted.remove(task.getSchemaName() + "\u0000" + task.getTableName()) != null) {
                result.add(task);
            }
        }
        if (!wanted.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SYNC_TASK_NOT_FOUND",
                    "部分表尚未创建同步任务",
                    Map.of("tables", wanted.values().stream()
                            .map(item -> Map.of("schema", item.schema(), "table", item.table()))
                            .toList())
            );
        }
        return result;
    }

    private String shortId(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }
}
