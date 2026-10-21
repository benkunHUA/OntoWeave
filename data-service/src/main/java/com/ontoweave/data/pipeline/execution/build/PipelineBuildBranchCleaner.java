package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PipelineBuildBranchCleaner {

    private static final Logger log = LoggerFactory.getLogger(PipelineBuildBranchCleaner.class);

    private final PipelineBuildOutputRepository buildOutputs;
    private final PipelineDatasetRepository datasets;
    private final PipelineDatasetWriter writer;

    public PipelineBuildBranchCleaner(
            PipelineBuildOutputRepository buildOutputs,
            PipelineDatasetRepository datasets,
            PipelineDatasetWriter writer
    ) {
        this.buildOutputs = buildOutputs;
        this.datasets = datasets;
        this.writer = writer;
    }

    public void cleanup(UUID buildId) {
        String branch = PipelineBuildConstants.BRANCH_PREFIX + buildId;
        for (PipelineBuildOutputEntity row : buildOutputs.findByBuildIdOrderByCreatedAtAsc(buildId)) {
            PipelineDatasetEntity dataset = datasets.findByOutputId(row.getOutputId()).orElse(null);
            if (dataset == null) {
                continue;
            }
            try {
                writer.dropBranch(dataset.getId(), branch);
            } catch (RuntimeException error) {
                log.warn("failed to drop failed build branch {} for dataset {}",
                        branch, dataset.getId(), error);
            }
        }
    }
}
