package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStage;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PipelineBuildTerminalWriter {

    private static final Logger log = LoggerFactory.getLogger(PipelineBuildTerminalWriter.class);

    private final PipelineBuildRepository builds;
    private final PipelineBuildOutputRepository outputs;
    private final PipelineDatasetWriter writer;
    private final PipelineBuildBranchCleaner branchCleaner;
    private final PipelineBuildLogPersistence logs;

    public PipelineBuildTerminalWriter(
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository outputs,
            PipelineDatasetWriter writer,
            PipelineBuildBranchCleaner branchCleaner,
            PipelineBuildLogPersistence logs
    ) {
        this.builds = builds;
        this.outputs = outputs;
        this.writer = writer;
        this.branchCleaner = branchCleaner;
        this.logs = logs;
    }

    public void cancel(PipelineBuildEntity build) {
        PipelineBuildEntity current = builds.findById(build.getId()).orElse(build);
        if (terminal(current.getStatus())) return;
        try {
            writer.cancel(build.getId());
        } catch (RuntimeException error) {
            log.warn("failed to cancel engine job for build {}", build.getId(), error);
            return;
        }
        Instant now = Instant.now();
        for (PipelineBuildOutputEntity row : outputs.findByBuildIdOrderByCreatedAtAsc(current.getId())) {
            logs.carryOutputLogUri(current, row.getOutputId());
            if (active(row.getStatus())) {
                row.markCanceled(now);
                outputs.save(row);
            }
        }
        current.markCanceled(now);
        builds.save(current);
        branchCleaner.cleanup(current.getId());
    }

    public void fail(PipelineBuildEntity build, String code, String message) {
        PipelineBuildEntity current = builds.findById(build.getId()).orElse(build);
        if (terminal(current.getStatus())) return;
        Instant now = Instant.now();
        for (PipelineBuildOutputEntity row : outputs.findByBuildIdOrderByCreatedAtAsc(current.getId())) {
            logs.carryOutputLogUri(current, row.getOutputId());
            if (active(row.getStatus())) {
                row.markFailed(
                        PipelineBuildStage.FINISHED, code, message,
                        row.getResult(), row.getStats(), now
                );
                outputs.save(row);
            }
        }
        current.markFailed(code, message, now);
        builds.save(current);
        branchCleaner.cleanup(current.getId());
    }

    private boolean active(PipelineOutputStatus status) {
        return status == PipelineOutputStatus.RUNNING || status == PipelineOutputStatus.QUEUED;
    }

    private boolean terminal(PipelineBuildStatus status) {
        return status == PipelineBuildStatus.SUCCEEDED
                || status == PipelineBuildStatus.FAILED
                || status == PipelineBuildStatus.CANCELED;
    }
}
