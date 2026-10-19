package com.ontoweave.data.sync.execution;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.domain.VersionSource;
import com.ontoweave.data.sync.flink.FlinkCheckpointInfo;
import com.ontoweave.data.sync.flink.FlinkJobInfo;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import com.ontoweave.data.sync.version.DatasetVersionPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class SyncRunSupervisorTest {

    @Mock
    SyncRunRepository runs;

    @Mock
    FlinkRestClient flink;

    @Mock
    IcebergCatalogClient iceberg;

    @Mock
    DatasetVersionPublisher publisher;

    @Mock
    SyncTaskRepository tasks;

    @Mock
    SyncRunEventWriter events;

    @Mock
    SyncRunStopInitiator stopInitiator;

    @Test
    void publishesInitialSnapshotVersionImmediately() {
        SyncRunEntity run = initialRun();
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "RUNNING")));
        when(flink.latestCompletedCheckpoint(run.getFlinkJobId())).thenReturn(Optional.of(
                new FlinkCheckpointInfo(3, Instant.now(), null)));
        when(iceberg.latestSnapshot(any(), any(), any())).thenReturn(Optional.of(
                new IcebergSnapshotInfo(
                        1L,
                        Instant.now(),
                        10L,
                        0L,
                        JsonNodeFactory.instance.objectNode(),
                        1,
                        "file:/tmp/table"
                )));
        when(publisher.publish(run.getId(), VersionSource.SNAPSHOT_END))
                .thenReturn(Optional.of(mock(DatasetVersionEntity.class)));
        when(runs.markRunning(any(), eq(SyncRunStatus.SUBMITTED), eq(SyncRunStatus.RUNNING), any(), any()))
                .thenReturn(1);
        when(runs.recordProgress(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        supervisor().reconcile();

        verify(runs, never()).save(any());
        verify(publisher).publish(run.getId(), VersionSource.SNAPSHOT_END);
        assertThat(run.getSnapshotCompletedAt()).isNotNull();
    }

    @Test
    void neverOverwritesRunWithStaleEntityAfterPublishingScheduledVersion() {
        SyncRunEntity run = dueRun();
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "RUNNING")));
        when(flink.latestCompletedCheckpoint(run.getFlinkJobId())).thenReturn(Optional.of(
                new FlinkCheckpointInfo(3, Instant.now().minusSeconds(10), null)));
        when(publisher.publish(run.getId(), VersionSource.SCHEDULED))
                .thenReturn(Optional.of(mock(DatasetVersionEntity.class)));
        when(runs.markRunning(any(), eq(SyncRunStatus.SUBMITTED), eq(SyncRunStatus.RUNNING), any(), any()))
                .thenReturn(1);
        when(runs.recordProgress(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        supervisor().reconcile();

        verify(runs, never()).save(any());
        verify(publisher).publish(run.getId(), VersionSource.SCHEDULED);
    }

    @Test
    void schedulesNextAttemptWhenNoNewSnapshotWasPublished() {
        SyncRunEntity run = dueRun();
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "RUNNING")));
        when(flink.latestCompletedCheckpoint(run.getFlinkJobId())).thenReturn(Optional.of(
                new FlinkCheckpointInfo(3, Instant.now().minusSeconds(10), null)));
        when(publisher.publish(run.getId(), VersionSource.SCHEDULED)).thenReturn(Optional.empty());
        when(runs.markRunning(any(), eq(SyncRunStatus.SUBMITTED), eq(SyncRunStatus.RUNNING), any(), any()))
                .thenReturn(1);
        when(runs.recordProgress(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(runs.scheduleNextVersion(any(), eq(SyncRunStatus.RUNNING), any(), any())).thenReturn(1);

        supervisor().reconcile();

        verify(runs, never()).save(any());
        verify(publisher).publish(run.getId(), VersionSource.SCHEDULED);
        verify(runs).scheduleNextVersion(any(), eq(SyncRunStatus.RUNNING), any(), any());
        assertThat(run.getNextVersionDueAt()).isAfter(Instant.now().plusSeconds(50));
    }

    @Test
    void recordsFlinkFailureDetailsForAgentDiagnosis() {
        SyncRunEntity run = initialRun();
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "FAILED")));
        when(flink.failureSummary(run.getFlinkJobId())).thenReturn(Optional.of(
                "Flink 作业失败：NoResourceAvailableException: Could not acquire the minimum required resources"));
        when(runs.markFailed(
                any(), eq(SyncRunStatus.SUBMITTED), eq(SyncRunStatus.FAILED),
                eq("FAILED"), eq("FLINK_JOB_FAILED"), any(), any()
        )).thenReturn(1);

        supervisor().reconcile();

        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo("FLINK_JOB_FAILED");
        assertThat(run.getErrorMessage()).contains("NoResourceAvailableException");
    }

    @Test
    void doesNotPublishProgressAfterStopRequestWonTheRace() {
        SyncRunEntity run = initialRun();
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "RUNNING")));
        when(runs.markRunning(any(), eq(SyncRunStatus.SUBMITTED), eq(SyncRunStatus.RUNNING), any(), any()))
                .thenReturn(1);
        when(runs.recordProgress(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        supervisor().reconcile();

        verify(runs, never()).save(any());
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void delegatesStopRequestToInitiatorWhileJobIsStillRunning() {
        SyncRunEntity run = initialRun();
        run.markStopping();
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "RUNNING")));
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));

        supervisor().reconcile();

        verify(stopInitiator).initiate(run);
        verify(flink, never()).stopWithSavepoint(any(), any());
        verify(flink, never()).cancel(any());
    }

    @Test
    void finishesStoppingRunWhenFlinkJobWasCanceled() {
        SyncRunEntity run = initialRun();
        run.markStopping();
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "CANCELED")));
        when(runs.markStopped(
                eq(run.getId()), eq(SyncRunStatus.STOPPING), eq(SyncRunStatus.STOPPED),
                eq(run.getFlinkJobId()), any(), any(), any(), any(), any()
        )).thenReturn(1);

        supervisor().reconcile();

        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.STOPPED);
        verify(stopInitiator, never()).initiate(any());
    }

    @Test
    void cancelsFlinkJobWhenSavepointStopTimesOut() {
        SyncRunEntity run = initialRun();
        run.markStopping();
        run.markStopRequested(Instant.now().minusSeconds(60));
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "RUNNING")));
        when(runs.claimCancelFallback(eq(run.getId()), eq(SyncRunStatus.STOPPING), any(), any()))
                .thenReturn(1);

        supervisor().reconcile();

        verify(flink).cancel(run.getFlinkJobId());
    }

    private SyncRunSupervisor supervisor() {
        SyncProperties properties = new SyncProperties(
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
        return new SyncRunSupervisor(properties, runs, flink, iceberg, publisher, tasks, events, stopInitiator);
    }

    private SyncRunEntity dueRun() {
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
                60
        );
        run.markSubmitted("job-id");
        run.markSnapshotCompleted(Instant.now().minusSeconds(120));
        run.recordCheckpoint(Instant.now().minusSeconds(30), null);
        run.recordPublishedSnapshot(1L);
        run.markSnapshotCompleted(Instant.now().minusSeconds(120));
        return run;
    }

    private SyncRunEntity initialRun() {
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
        run.recordSnapshotBaseline(10);
        return run;
    }
}
