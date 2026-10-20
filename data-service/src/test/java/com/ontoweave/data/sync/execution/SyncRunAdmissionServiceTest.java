package com.ontoweave.data.sync.execution;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncRunAdmissionServiceTest {

    @Mock
    SyncRunRepository runs;

    @Mock
    SyncTaskRepository tasks;

    @Mock
    FlinkRestClient flink;

    @Mock
    PlatformTransactionManager transactionManager;

    @Test
    void admitsQueuedRunWhenCapacityAndSlotsAreAvailable() {
        SyncRunEntity run = queuedRun();
        SyncTaskEntity task = task(run);
        transaction();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));
        when(runs.countByStatusIn(any())).thenReturn(0L);
        when(runs.countByConnectionIdAndStatusIn(eq(task.getConnectionId()), any())).thenReturn(0L);
        when(flink.availableTaskSlots()).thenReturn(Optional.of(4));
        when(runs.claim(eq(run.getId()), eq(SyncRunStatus.QUEUED), eq(SyncRunStatus.PREPARING), any()))
                .thenReturn(1);

        assertThat(service(4, 1).tryAdmit(run.getId())).isTrue();
    }

    @Test
    void rejectsRunAtGlobalConcurrencyLimit() {
        SyncRunEntity run = queuedRun();
        SyncTaskEntity task = task(run);
        transaction();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));
        when(runs.countByStatusIn(any())).thenReturn(4L);

        assertThat(service(4, 1).tryAdmit(run.getId())).isFalse();
        verify(runs).releaseDispatch(eq(run.getId()), eq(SyncRunStatus.QUEUED), any());
        verify(runs, never()).claim(any(), any(), any(), any());
    }

    @Test
    void rejectsSecondRunOnSameConnection() {
        SyncRunEntity run = queuedRun();
        SyncTaskEntity task = task(run);
        transaction();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));
        when(runs.countByStatusIn(any())).thenReturn(1L);
        when(runs.countByConnectionIdAndStatusIn(eq(task.getConnectionId()), any())).thenReturn(1L);

        assertThat(service(4, 1).tryAdmit(run.getId())).isFalse();
        verify(runs).releaseDispatch(eq(run.getId()), eq(SyncRunStatus.QUEUED), any());
    }

    @Test
    void rejectsRunWhenFlinkHasNoEnoughSlots() {
        SyncRunEntity run = queuedRun();
        SyncTaskEntity task = task(run);
        transaction();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(tasks.findById(run.getSyncTaskId())).thenReturn(Optional.of(task));
        when(runs.countByStatusIn(any())).thenReturn(0L);
        when(runs.countByConnectionIdAndStatusIn(eq(task.getConnectionId()), any())).thenReturn(0L);
        when(flink.availableTaskSlots()).thenReturn(Optional.of(1));

        assertThat(service(4, 1).tryAdmit(run.getId())).isFalse();
        verify(runs).releaseDispatch(eq(run.getId()), eq(SyncRunStatus.QUEUED), any());
    }

    private void transaction() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    private SyncRunAdmissionService service(int globalLimit, int connectionLimit) {
        return new SyncRunAdmissionService(
                properties(globalLimit, connectionLimit),
                runs,
                tasks,
                flink,
                transactionManager
        );
    }

    private SyncProperties properties(int globalLimit, int connectionLimit) {
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
                30,
                globalLimit,
                connectionLimit
        );
    }

    private SyncRunEntity queuedRun() {
        return new SyncRunEntity(
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
}
