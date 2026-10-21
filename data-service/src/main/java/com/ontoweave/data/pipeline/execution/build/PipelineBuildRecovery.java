package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStage;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PipelineBuildRecovery {

    public static final int BATCH_SIZE = 100;

    private final PipelineBuildRepository builds;
    private final PipelineBuildOutputRepository outputs;

    public PipelineBuildRecovery(
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository outputs
    ) {
        this.builds = builds;
        this.outputs = outputs;
    }

    @Transactional
    public List<LostBuild> markLost(Duration timeout) {
        Instant cutoff = Instant.now().minus(timeout);
        List<PipelineBuildEntity> stale = builds.lockStaleRunning(cutoff, BATCH_SIZE);
        List<LostBuild> recovered = new ArrayList<>();
        for (PipelineBuildEntity build : stale) {
            if (build.getStatus() != PipelineBuildStatus.RUNNING || !stale(build, cutoff)) {
                continue;
            }
            List<PipelineBuildOutputEntity> rows = outputs.findByBuildIdOrderByCreatedAtAsc(build.getId());
            List<String> engineJobs = new ArrayList<>();
            for (PipelineBuildOutputEntity row : rows) {
                if (row.getEngineJobId() != null && !row.getEngineJobId().isBlank()) {
                    engineJobs.add(row.getEngineJobId());
                }
            }
            Instant now = Instant.now();
            boolean canceled = build.getCancelRequestedAt() != null;
            for (PipelineBuildOutputEntity row : rows) {
                if (row.getStatus() != PipelineOutputStatus.RUNNING
                        && row.getStatus() != PipelineOutputStatus.QUEUED) {
                    continue;
                }
                if (canceled) {
                    row.markCanceled(now);
                } else {
                    row.markFailed(
                            PipelineBuildStage.FINISHED,
                            PipelineBuildConstants.CODE_WORKER_LOST,
                            "构建 worker 心跳超时",
                            row.getResult(),
                            row.getStats(),
                            now
                    );
                }
                outputs.save(row);
            }
            if (canceled) {
                build.markCanceled(now);
            } else {
                build.markFailed(
                        PipelineBuildConstants.CODE_WORKER_LOST,
                        "构建 worker 心跳超时",
                        now
                );
            }
            builds.save(build);
            recovered.add(new LostBuild(build.getId(), canceled, List.copyOf(engineJobs)));
        }
        return List.copyOf(recovered);
    }

    private boolean stale(PipelineBuildEntity build, Instant cutoff) {
        return build.getHeartbeatAt() == null || build.getHeartbeatAt().isBefore(cutoff);
    }

    public record LostBuild(UUID buildId, boolean canceled, List<String> engineJobIds) {

        public LostBuild {
            engineJobIds = engineJobIds == null ? List.of() : List.copyOf(engineJobIds);
        }
    }
}
