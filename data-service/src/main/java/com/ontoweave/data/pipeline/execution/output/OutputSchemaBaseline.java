package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import org.springframework.stereotype.Component;

@Component
public class OutputSchemaBaseline {

    private final PipelineDatasetVersionRepository versions;

    public OutputSchemaBaseline(PipelineDatasetVersionRepository versions) {
        this.versions = versions;
    }

    public JsonNode resolve(PipelineDatasetEntity dataset) {
        if (dataset == null || dataset.getCurrentVersionId() == null) {
            return null;
        }
        return versions.findByIdAndDatasetId(dataset.getCurrentVersionId(), dataset.getId())
                .map(PipelineDatasetVersionEntity::getSchemaJson)
                .orElse(null);
    }
}
