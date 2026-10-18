package com.ontoweave.data.sync.execution;

import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.VersionSource;
import com.ontoweave.data.sync.flink.FlinkCheckpointInfo;
import com.ontoweave.data.sync.flink.FlinkJobInfo;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import com.ontoweave.data.sync.version.DatasetVersionPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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

    @Test
    void doesNotSaveStaleRunAfterPublishingScheduledVersion() {
        SyncRunEntity run = dueRun();
        when(runs.findTop50ByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(run));
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(new FlinkJobInfo(
                run.getFlinkJobId(), run.getFlinkJobName(), "RUNNING")));
        when(flink.latestCompletedCheckpoint(run.getFlinkJobId())).thenReturn(Optional.of(
                new FlinkCheckpointInfo(3, Instant.now().minusSeconds(10), null)));
        when(publisher.publish(run.getId(), VersionSource.SCHEDULED))
                .thenReturn(Optional.of(mock(DatasetVersionEntity.class)));

        supervisor().reconcile();

        InOrder order = inOrder(runs, publisher);
        order.verify(runs).save(run);
        order.verify(publisher).publish(run.getId(), VersionSource.SCHEDULED);
        order.verifyNoMoreInteractions();
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

        supervisor().reconcile();

        InOrder order = inOrder(runs, publisher);
        order.verify(runs).save(run);
        order.verify(publisher).publish(run.getId(), VersionSource.SCHEDULED);
        order.verify(runs).save(run);
        order.verifyNoMoreInteractions();
        assertThat(run.getNextVersionDueAt()).isAfter(Instant.now().plusSeconds(50));
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
                30
        );
        return new SyncRunSupervisor(properties, runs, flink, iceberg, publisher, tasks, events);
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
        return run;
    }
}
