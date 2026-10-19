package com.ontoweave.data.sync.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.connection.service.DataConnectionService;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.execution.SyncRunEventWriter;
import com.ontoweave.data.sync.execution.SyncRunStopInitiator;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncRunServiceTest {

    @Mock
    DataConnectionService connections;

    @Mock
    SyncTaskRepository tasks;

    @Mock
    SyncRunRepository runs;

    @Mock
    SyncRunEventWriter events;

    @Mock
    SyncRunStopInitiator stopInitiator;

    @Mock
    PlatformTransactionManager transactionManager;

    @Test
    void marksStoppingThenImmediatelyInitiatesFlinkStop() {
        SyncRunEntity run = activeRun();
        SyncTaskEntity task = task(run);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        doAnswer(invocation -> {
            run.markStopping();
            return 1;
        }).when(runs).requestStop(eq(run.getId()), any(), eq(SyncRunStatus.STOPPING), any());

        var response = service().stop(run.getId(), UUID.randomUUID());

        assertThat(response.status()).isEqualTo("stopping");
        verify(events).write(run, task, "sync.run.stopping", "已请求停止");
        verify(stopInitiator).initiate(run);
    }

    @Test
    void keepsStoppingWhenImmediateFlinkCallFails() {
        SyncRunEntity run = activeRun();
        SyncTaskEntity task = task(run);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        doAnswer(invocation -> {
            run.markStopping();
            return 1;
        }).when(runs).requestStop(eq(run.getId()), any(), eq(SyncRunStatus.STOPPING), any());
        doThrow(new IllegalStateException("flink down")).when(stopInitiator).initiate(any());

        var response = service().stop(run.getId(), UUID.randomUUID());

        assertThat(response.status()).isEqualTo("stopping");
    }

    @Test
    void doesNothingForTerminalRun() {
        SyncRunEntity run = activeRun();
        run.markSucceeded();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        SyncTaskEntity task = task(run);
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));

        var response = service().stop(run.getId(), UUID.randomUUID());

        assertThat(response.status()).isEqualTo("succeeded");
        verify(runs, never()).requestStop(any(), any(), any(), any());
        verify(stopInitiator, never()).initiate(any());
    }

    @Test
    void doesNotRewriteEventWhenStopWasAlreadyRequested() {
        SyncRunEntity run = activeRun();
        run.markStopping();
        SyncTaskEntity task = task(run);
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(runs.requestStop(eq(run.getId()), any(), eq(SyncRunStatus.STOPPING), any())).thenReturn(0);

        service().stop(run.getId(), UUID.randomUUID());

        verify(events, never()).write(any(), any(), eq("sync.run.stopping"), any());
        verify(stopInitiator, never()).initiate(any());
    }

    private SyncRunService service() {
        return new SyncRunService(
                connections,
                tasks,
                runs,
                events,
                properties(),
                stopInitiator,
                transactionManager
        );
    }

    private SyncRunEntity activeRun() {
        SyncRunEntity run = new SyncRunEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SyncMode.CDC,
                "job-name",
                "ontoweave",
                "db",
                "tbl",
                86400
        );
        run.markSubmitted("job-id");
        run.recordFlinkState("RUNNING");
        return run;
    }

    private SyncTaskEntity task(SyncRunEntity run) {
        return new SyncTaskEntity(
                run.getSyncTaskId(),
                UUID.randomUUID(),
                run.getDatasetId(),
                "demo",
                "orders",
                SyncMode.CDC,
                86400,
                JsonNodeFactory.instance.arrayNode()
        );
    }

    private SyncProperties properties() {
        return new SyncProperties(
                "http://localhost:8081",
                "/opt/flink",
                "/opt/flink-cdc",
                "ontoweave",
                "/tmp/warehouse",
                "file:///tmp/checkpoints",
                "file:///tmp/savepoints",
                2,
                100,
                "commands",
                "sync.execute",
                true,
                true,
                5,
                86400,
                30,
                30
        );
    }
}
