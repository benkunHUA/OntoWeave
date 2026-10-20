package com.ontoweave.data.sync.execution;

import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.flink.FlinkJobInfo;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncRunStopInitiatorTest {

    @Mock
    SyncRunRepository runs;

    @Mock
    FlinkRestClient flink;

    @Test
    void stopsCdcJobWithSavepoint() {
        SyncRunEntity run = stoppingRun(SyncMode.CDC);
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(job(run, "RUNNING")));
        when(runs.claimStopRequest(eq(run.getId()), eq(SyncRunStatus.STOPPING), any())).thenReturn(1);
        when(flink.stopWithSavepoint(run.getFlinkJobId(), "file:///tmp/savepoints/" + run.getId()))
                .thenReturn(Optional.of("request-id"));

        initiator().initiate(run);

        verify(flink).stopWithSavepoint(run.getFlinkJobId(), "file:///tmp/savepoints/" + run.getId());
        verify(runs).recordSavepointRequest(
                eq(run.getId()), eq(SyncRunStatus.STOPPING), eq("request-id"), any()
        );
        verify(flink, never()).cancel(any());
        assertThat(run.getStopRequestedAt()).isNotNull();
    }

    @Test
    void fallsBackToCancelWhenSavepointRequestFails() {
        SyncRunEntity run = stoppingRun(SyncMode.CDC);
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(job(run, "RUNNING")));
        when(runs.claimStopRequest(eq(run.getId()), eq(SyncRunStatus.STOPPING), any())).thenReturn(1);
        when(flink.stopWithSavepoint(eq(run.getFlinkJobId()), any())).thenReturn(Optional.empty());

        initiator().initiate(run);

        verify(flink).cancel(run.getFlinkJobId());
        assertThat(run.getStopRequestedAt()).isNotNull();
    }

    @Test
    void cancelsSnapshotJobDirectly() {
        SyncRunEntity run = stoppingRun(SyncMode.SNAPSHOT);
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(job(run, "RUNNING")));
        when(runs.claimStopRequest(eq(run.getId()), eq(SyncRunStatus.STOPPING), any())).thenReturn(1);

        initiator().initiate(run);

        verify(flink).cancel(run.getFlinkJobId());
        verify(flink, never()).stopWithSavepoint(any(), any());
    }

    @Test
    void skipsWhenStopWasAlreadyRequested() {
        SyncRunEntity run = stoppingRun(SyncMode.CDC);
        run.markStopRequested(Instant.now());

        initiator().initiate(run);

        verify(flink, never()).job(any());
        verify(flink, never()).stopWithSavepoint(any(), any());
        verify(flink, never()).cancel(any());
    }

    @Test
    void skipsWhenJobIsAlreadyTerminal() {
        SyncRunEntity run = stoppingRun(SyncMode.CDC);
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(job(run, "FINISHED")));

        initiator().initiate(run);

        verify(runs, never()).claimStopRequest(any(), any(), any());
        verify(flink, never()).cancel(any());
    }

    @Test
    void skipsWhenAnotherCallerClaimedStopRequest() {
        SyncRunEntity run = stoppingRun(SyncMode.CDC);
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(job(run, "RUNNING")));
        when(runs.claimStopRequest(eq(run.getId()), eq(SyncRunStatus.STOPPING), any())).thenReturn(0);

        initiator().initiate(run);

        verify(flink, never()).stopWithSavepoint(any(), any());
        verify(flink, never()).cancel(any());
    }

    @Test
    void releasesClaimWhenFlinkCallFails() {
        SyncRunEntity run = stoppingRun(SyncMode.SNAPSHOT);
        when(flink.job(run.getFlinkJobId())).thenReturn(Optional.of(job(run, "RUNNING")));
        when(runs.claimStopRequest(eq(run.getId()), eq(SyncRunStatus.STOPPING), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("flink down")).when(flink).cancel(run.getFlinkJobId());

        assertThatThrownBy(() -> initiator().initiate(run)).isInstanceOf(IllegalStateException.class);

        verify(runs).releaseStopRequest(eq(run.getId()), eq(SyncRunStatus.STOPPING), any(), any());
    }

    private SyncRunStopInitiator initiator() {
        return new SyncRunStopInitiator(properties(), runs, flink);
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
                30,
                4,
                1
        );
    }

    private SyncRunEntity stoppingRun(SyncMode mode) {
        SyncRunEntity run = new SyncRunEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                mode,
                "job-name",
                "ontoweave",
                "db",
                "tbl",
                86400
        );
        run.markSubmitted("job-id");
        run.markStopping();
        return run;
    }

    private FlinkJobInfo job(SyncRunEntity run, String state) {
        return new FlinkJobInfo(run.getFlinkJobId(), run.getFlinkJobName(), state);
    }
}
