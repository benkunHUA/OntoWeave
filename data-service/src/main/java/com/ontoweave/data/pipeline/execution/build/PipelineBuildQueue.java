package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStage;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PipelineBuildQueue {

    private static final int CLAIM_ATTEMPTS = 10;

    private final PipelineBuildRepository builds;
    private final PipelineBuildOutputRepository outputs;
    private final FlinkRestClient flink;
    private final PipelineBuildHeartbeat heartbeat;
    private final PipelineBuildRecovery recovery;
    private final PipelineBuildBranchCleaner branchCleaner;

    public PipelineBuildQueue(
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository outputs,
            FlinkRestClient flink,
            PipelineBuildHeartbeat heartbeat,
            PipelineBuildRecovery recovery,
            PipelineBuildBranchCleaner branchCleaner
    ) {
        this.builds = builds;
        this.outputs = outputs;
        this.flink = flink;
        this.heartbeat = heartbeat;
        this.recovery = recovery;
        this.branchCleaner = branchCleaner;
    }

    @Transactional
    public UUID claimNext() {
        Instant now = Instant.now();
        for (int attempt = 0; attempt < CLAIM_ATTEMPTS; attempt++) {
            List<PipelineBuildEntity> locked = builds.lockQueued(1);
            if (locked.isEmpty()) {
                return null;
            }
            PipelineBuildEntity build = locked.get(0);
            if (build.getCancelRequestedAt() != null) {
                for (PipelineBuildOutputEntity row
                        : outputs.findByBuildIdOrderByCreatedAtAsc(build.getId())) {
                    if (row.getStatus() == PipelineOutputStatus.QUEUED
                            || row.getStatus() == PipelineOutputStatus.RUNNING) {
                        row.markCanceled(now);
                        outputs.save(row);
                    }
                }
                build.markCanceled(now);
                builds.save(build);
                continue;
            }
            build.markRunning(PipelineBuildStage.RESOLVING_INPUTS, now);
            builds.save(build);
            return build.getId();
        }
        return null;
    }

    public boolean heartbeat(UUID buildId) {
        return heartbeat.mark(buildId);
    }

    public int recoverLost(Duration timeout) {
        int recovered = 0;
        while (true) {
            List<PipelineBuildRecovery.LostBuild> lost = recovery.markLost(timeout);
            if (lost.isEmpty()) {
                return recovered;
            }
            for (PipelineBuildRecovery.LostBuild build : lost) {
                for (String jobId : build.engineJobIds()) {
                    cancelEngineJob(jobId);
                }
                branchCleaner.cleanup(build.buildId());
            }
            recovered += lost.size();
            if (lost.size() < PipelineBuildRecovery.BATCH_SIZE) {
                return recovered;
            }
        }
    }

    @Transactional
    public void requeue(UUID buildId) {
        builds.lockById(buildId)
                .filter(build -> build.getStatus() == PipelineBuildStatus.RUNNING)
                .ifPresent(build -> {
                    build.markQueued();
                    builds.save(build);
                });
    }

    private void cancelEngineJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        try {
            flink.cancel(jobId);
        } catch (RuntimeException ignored) {
            // The job may already be gone; database recovery remains the source of truth.
        }
    }
}
