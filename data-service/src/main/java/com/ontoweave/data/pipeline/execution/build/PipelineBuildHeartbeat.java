package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PipelineBuildHeartbeat {

    private final PipelineBuildRepository builds;

    public PipelineBuildHeartbeat(PipelineBuildRepository builds) {
        this.builds = builds;
    }

    @Transactional
    public boolean mark(UUID buildId) {
        return builds.lockById(buildId)
                .filter(build -> build.getStatus() == PipelineBuildStatus.RUNNING)
                .map(this::refresh)
                .orElse(false);
    }

    private boolean refresh(PipelineBuildEntity build) {
        build.heartbeat(Instant.now());
        builds.save(build);
        return build.getCancelRequestedAt() != null;
    }
}
