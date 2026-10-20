package com.ontoweave.data.sync.execution;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import com.ontoweave.data.sync.flink.MysqlCdcPipelineBuilder;
import com.ontoweave.data.sync.flink.PipelineSpec;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import com.ontoweave.data.sync.service.MysqlCdcPreflight;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncRunExecutorTest {

    @Mock
    SyncRunRepository runs;

    @Mock
    SyncTaskRepository tasks;

    @Mock
    ConnectionMaterialService materials;

    @Mock
    MysqlTableDiscovery discovery;

    @Mock
    MysqlCdcPreflight cdcPreflight;

    @Mock
    ServerIdAllocator serverIds;

    @Mock
    MysqlCdcPipelineBuilder pipelines;

    @Mock
    FlinkCdcSubmitter submitter;

    @Mock
    FlinkRestClient flink;

    @Mock
    SyncRunEventWriter events;

    @Mock
    SyncRunAdmissionService admission;

    @Mock
    SyncRunCleanupService cleanup;

    @Test
    void cancelsSubmittedJobWhenStopWinsBeforeStatusUpdate() {
        SyncRunEntity run = preparingRun();
        SyncTaskEntity task = task(run);
        when(admission.tryAdmit(run.getId())).thenReturn(true);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));
        when(materials.loadMysqlInternal(task.getConnectionId()))
                .thenReturn(new MysqlConnectionMaterial(null, null, "password"));
        when(flink.findJobByName(run.getFlinkJobName())).thenReturn(Optional.empty());
        when(discovery.describe(any(), any(), eq(task.getSchemaName()), eq(task.getTableName())))
                .thenReturn(new TableDetailResponse("demo", "orders", "TABLE", null, List.of(), List.of()));
        when(serverIds.allocate()).thenReturn(new long[]{1L, 2L});
        when(runs.recordPrepared(
                eq(run.getId()), isNull(), anyLong(), anyLong(), eq(SyncRunStatus.PREPARING), any(Instant.class)
        )).thenReturn(1);
        when(pipelines.build(any(), any(), any(), any(), any())).thenReturn(new PipelineSpec("job-name", "yaml"));
        when(submitter.submit(any())).thenReturn(new FlinkCdcSubmitter.SubmitResult(Optional.of("job-1"), "output"));
        when(runs.markSubmitted(
                eq(run.getId()), eq("job-1"), eq(SyncRunStatus.PREPARING), eq(SyncRunStatus.SUBMITTED), any(Instant.class)
        )).thenReturn(0);
        when(runs.attachSubmittedJob(eq(run.getId()), eq("job-1"), eq(SyncRunStatus.STOPPING), any(Instant.class)))
                .thenReturn(0);

        executor().execute(run.getId());

        verify(flink).cancel("job-1");
        verify(events, never()).write(any(), any(), eq("sync.run.submitted"), any());
    }

    private SyncRunExecutor executor() {
        return new SyncRunExecutor(
                runs,
                tasks,
                materials,
                discovery,
                cdcPreflight,
                serverIds,
                pipelines,
                submitter,
                flink,
                events,
                admission,
                cleanup
        );
    }

    private SyncRunEntity preparingRun() {
        SyncRunEntity run = new SyncRunEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SyncMode.SNAPSHOT,
                "job-name",
                "ontoweave",
                "db",
                "tbl",
                86400
        );
        ReflectionTestUtils.setField(run, "status", SyncRunStatus.PREPARING);
        return run;
    }

    private SyncTaskEntity task(SyncRunEntity run) {
        return new SyncTaskEntity(
                run.getSyncTaskId(),
                UUID.randomUUID(),
                run.getDatasetId(),
                "demo",
                "orders",
                SyncMode.SNAPSHOT,
                86400,
                JsonNodeFactory.instance.arrayNode()
        );
    }
}
