package com.ontoweave.data.sync.execution;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.api.dto.TableDetailResponse;
import com.ontoweave.data.connection.service.ConnectionMaterialService;
import com.ontoweave.data.connection.service.ConnectionMaterialService.MysqlConnectionMaterial;
import com.ontoweave.data.connector.mysql.MysqlTableDiscovery;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.flink.FlinkCdcSubmitter;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import com.ontoweave.data.sync.flink.FlinkSubmitException;
import com.ontoweave.data.sync.flink.MysqlCdcPipelineBuilder;
import com.ontoweave.data.sync.flink.PipelineSpec;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import com.ontoweave.data.sync.service.MysqlCdcPreflight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SyncRunExecutor {

    private static final Logger log = LoggerFactory.getLogger(SyncRunExecutor.class);

    private final SyncRunRepository runs;
    private final SyncTaskRepository tasks;
    private final ConnectionMaterialService materials;
    private final MysqlTableDiscovery discovery;
    private final MysqlCdcPreflight cdcPreflight;
    private final ServerIdAllocator serverIds;
    private final MysqlCdcPipelineBuilder pipelines;
    private final FlinkCdcSubmitter submitter;
    private final FlinkRestClient flink;
    private final SyncRunEventWriter events;

    public SyncRunExecutor(
            SyncRunRepository runs,
            SyncTaskRepository tasks,
            ConnectionMaterialService materials,
            MysqlTableDiscovery discovery,
            MysqlCdcPreflight cdcPreflight,
            ServerIdAllocator serverIds,
            MysqlCdcPipelineBuilder pipelines,
            FlinkCdcSubmitter submitter,
            FlinkRestClient flink,
            SyncRunEventWriter events
    ) {
        this.runs = runs;
        this.tasks = tasks;
        this.materials = materials;
        this.discovery = discovery;
        this.cdcPreflight = cdcPreflight;
        this.serverIds = serverIds;
        this.pipelines = pipelines;
        this.submitter = submitter;
        this.flink = flink;
        this.events = events;
    }

    public void execute(UUID runId) {
        if (runs.claim(runId, SyncRunStatus.QUEUED, SyncRunStatus.PREPARING, java.time.Instant.now()) != 1) {
            return;
        }
        SyncRunEntity run = runs.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        try {
            SyncTaskEntity task = tasks.findById(run.getSyncTaskId()).orElseThrow();
            MysqlConnectionMaterial material = materials.loadMysqlInternal(task.getConnectionId());
            Optional<String> existingJob = flink.findJobByName(run.getFlinkJobName())
                    .filter(job -> !"FAILED".equalsIgnoreCase(job.state()))
                    .filter(job -> !"CANCELED".equalsIgnoreCase(job.state()))
                    .map(job -> job.id());
            if (existingJob.isPresent()) {
                recordSubmitted(run, task, existingJob.get());
                return;
            }
            preflight(task, run, material);
            Long expectedRows = null;
            if (run.getRunMode() == SyncMode.CDC) {
                expectedRows = discovery.countRows(
                        material.config(),
                        material.password(),
                        task.getSchemaName(),
                        task.getTableName()
                );
            }
            long[] ids = serverIds.allocate();
            int prepared = runs.recordPrepared(
                    run.getId(),
                    expectedRows,
                    ids[0],
                    ids[1],
                    SyncRunStatus.PREPARING,
                    java.time.Instant.now()
            );
            if (prepared != 1) {
                return;
            }
            if (expectedRows != null) {
                run.recordSnapshotBaseline(expectedRows);
            }
            run.assignServerIds(ids[0], ids[1]);
            if (runs.findById(runId).filter(value -> value.getStatus() == SyncRunStatus.PREPARING).isEmpty()) {
                return;
            }
            PipelineSpec pipeline = pipelines.build(
                    run,
                    task,
                    material.config(),
                    material.password(),
                    discovery.serverTimeZone(material.config(), material.password())
            );
            FlinkCdcSubmitter.SubmitResult result = submitter.submit(pipeline);
            String jobId = result.jobId().orElseGet(() -> flink.findJobByName(run.getFlinkJobName())
                    .map(job -> job.id())
                    .orElse(null));
            if (jobId == null) {
                throw new FlinkSubmitException("FLINK_SUBMIT_FAILED", "未从提交输出中找到 Flink Job ID");
            }
            recordSubmitted(run, task, jobId);
        } catch (ApiException exception) {
            fail(run, exception.code(), exception.getMessage());
        } catch (FlinkSubmitException exception) {
            fail(run, exception.errorCode(), exception.getMessage());
        } catch (Exception exception) {
            log.error("sync run execution failed {}", runId, exception);
            fail(run, "UNKNOWN", "同步执行失败");
        }
    }

    private void preflight(SyncTaskEntity task, SyncRunEntity run, MysqlConnectionMaterial material) {
        TableDetailResponse detail = discovery.describe(
                material.config(),
                material.password(),
                task.getSchemaName(),
                task.getTableName()
        );
        List<String> expected = stringList(task.getPrimaryKeyColumns());
        if (!expected.equals(detail.primaryKeyColumns())) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "PRIMARY_KEY_CHANGED",
                    "源表主键已变化，请重新创建同步任务"
            );
        }
        if (run.getRunMode() == SyncMode.CDC) {
            cdcPreflight.validate(material);
        }
    }

    private void fail(SyncRunEntity run, String code, String message) {
        int updated = runs.markFailed(
                run.getId(),
                SyncRunStatus.PREPARING,
                SyncRunStatus.FAILED,
                null,
                code,
                message,
                java.time.Instant.now()
        );
        if (updated != 1) {
            return;
        }
        Optional<SyncTaskEntity> task = tasks.findById(run.getSyncTaskId());
        task.ifPresent(value -> events.write(run, value, "sync.run.failed", message));
    }

    private void recordSubmitted(SyncRunEntity run, SyncTaskEntity task, String jobId) {
        int updated = runs.markSubmitted(
                run.getId(),
                jobId,
                SyncRunStatus.PREPARING,
                SyncRunStatus.SUBMITTED,
                java.time.Instant.now()
        );
        if (updated == 1) {
            events.write(run, task, "sync.run.submitted", "Flink 作业已提交");
            return;
        }
        int attached = runs.attachSubmittedJob(
                run.getId(),
                jobId,
                SyncRunStatus.STOPPING,
                java.time.Instant.now()
        );
        if (attached == 0) {
            flink.cancel(jobId);
        }
    }

    private List<String> stringList(com.fasterxml.jackson.databind.JsonNode node) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> values.add(value.asText()));
        }
        return values;
    }
}
