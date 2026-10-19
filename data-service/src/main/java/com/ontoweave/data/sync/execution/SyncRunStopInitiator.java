package com.ontoweave.data.sync.execution;

import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.flink.FlinkJobInfo;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** 向 Flink 发起停止请求；状态确认由 Supervisor 负责。 */
@Component
public class SyncRunStopInitiator {

    private final SyncProperties properties;
    private final SyncRunRepository runs;
    private final FlinkRestClient flink;

    public SyncRunStopInitiator(SyncProperties properties, SyncRunRepository runs, FlinkRestClient flink) {
        this.properties = properties;
        this.runs = runs;
        this.flink = flink;
    }

    public void initiate(SyncRunEntity run) {
        if (run.getFlinkJobId() == null || run.getStopRequestedAt() != null) {
            return;
        }
        Optional<FlinkJobInfo> job = flink.job(run.getFlinkJobId());
        if (job.isEmpty() || terminal(job.get().state())) {
            return;
        }

        Instant now = Instant.now();
        if (runs.claimStopRequest(run.getId(), SyncRunStatus.STOPPING, now) != 1) {
            return;
        }
        try {
            if (run.getRunMode() == SyncMode.CDC) {
                String target = properties.savepointDir() + "/" + run.getId();
                if (flink.stopWithSavepoint(run.getFlinkJobId(), target).isEmpty()) {
                    flink.cancel(run.getFlinkJobId());
                }
            } else {
                flink.cancel(run.getFlinkJobId());
            }
            run.markStopRequested(now);
        } catch (RuntimeException exception) {
            runs.releaseStopRequest(run.getId(), SyncRunStatus.STOPPING, now, Instant.now());
            throw exception;
        }
    }

    private boolean terminal(String state) {
        return "CANCELED".equalsIgnoreCase(state)
                || "FINISHED".equalsIgnoreCase(state)
                || "FAILED".equalsIgnoreCase(state);
    }
}
