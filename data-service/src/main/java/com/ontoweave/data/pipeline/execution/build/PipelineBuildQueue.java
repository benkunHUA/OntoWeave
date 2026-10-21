package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStage;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
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
    private final PipelineDatasetWriter writer;
    private final FlinkRestClient flink;

    public PipelineBuildQueue(
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository outputs,
            PipelineDatasetWriter writer,
            FlinkRestClient flink
    ) {
        this.builds = builds;
        this.outputs = outputs;
        this.writer = writer;
        this.flink = flink;
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

    @Transactional
    public void heartbeat(UUID buildId) {
        builds.lockById(buildId)
                .filter(build -> build.getStatus() == PipelineBuildStatus.RUNNING)
                .ifPresent(build -> {
                    build.heartbeat(Instant.now());
                    builds.save(build);
                    if (build.getCancelRequestedAt() != null) {
                        writer.cancel(buildId);
                    }
                });
    }

    @Transactional
    public int recoverLost(Duration timeout) {
        Instant cutoff = Instant.now().minus(timeout);
        List<PipelineBuildEntity> stale = builds.findStaleRunning(PipelineBuildStatus.RUNNING, cutoff);
        for (PipelineBuildEntity build : stale) {
            List<PipelineBuildOutputEntity> rows = outputs.findByBuildIdOrderByCreatedAtAsc(build.getId());
            for (PipelineBuildOutputEntity row : rows) {
                cancelEngineJob(row.getEngineJobId());
                if (row.getStatus() == PipelineOutputStatus.RUNNING
                        || row.getStatus() == PipelineOutputStatus.QUEUED) {
                    row.markFailed(
                            PipelineBuildStage.FINISHED,
                            PipelineBuildConstants.CODE_WORKER_LOST,
                            "构建 worker 心跳超时",
                            row.getResult(),
                            row.getStats(),
                            Instant.now()
                    );
                    outputs.save(row);
                }
            }
            writer.cancel(build.getId());
            build.markFailed(
                    PipelineBuildConstants.CODE_WORKER_LOST,
                    "构建 worker 心跳超时",
                    Instant.now()
            );
            builds.save(build);
        }
        return stale.size();
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
