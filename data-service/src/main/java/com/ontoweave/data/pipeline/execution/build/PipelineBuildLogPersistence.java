package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PipelineBuildLogPersistence {

    private final PipelineBuildRepository builds;
    private final PipelineBuildOutputRepository outputs;

    public PipelineBuildLogPersistence(
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository outputs
    ) {
        this.builds = builds;
        this.outputs = outputs;
    }

    public void recordBuildLogUri(PipelineBuildEntity build, String logUri) {
        if (blank(logUri)) return;
        build.recordLogUri(logUri);
        builds.updateLogUriIfAbsent(build.getId(), logUri);
    }

    public void recordOutputEngineJob(
            UUID buildId,
            UUID outputId,
            String engineJobId,
            String logUri
    ) {
        if (blank(engineJobId)) return;
        outputs.updateEngineJobAndLogUri(buildId, outputId, engineJobId, logUri);
    }

    public void carryOutputLogUri(PipelineBuildEntity build, UUID outputId) {
        if (!blank(build.getLogUri())) return;
        outputs.findLogUriByBuildIdAndOutputId(build.getId(), outputId)
                .filter(uri -> !blank(uri))
                .ifPresent(uri -> recordBuildLogUri(build, uri));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
