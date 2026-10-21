package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PipelineBuildMapper {

    private final PipelineDatasetRepository datasets;
    private final PipelineDatasetVersionRepository datasetVersions;

    public PipelineBuildMapper(
            PipelineDatasetRepository datasets,
            PipelineDatasetVersionRepository datasetVersions
    ) {
        this.datasets = datasets;
        this.datasetVersions = datasetVersions;
    }

    public BuildResponse toResponse(PipelineBuildEntity build, List<PipelineBuildOutputEntity> outputs) {
        List<PipelineBuildOutputEntity> rows = outputs == null ? List.of() : outputs;
        Map<UUID, UUID> datasetIds = datasetsByOutputId(rows);
        Map<UUID, UUID> versionIds = versionsByBuildOutputId(rows);
        return new BuildResponse(
                build.getId(),
                build.getPipelineId(),
                build.getPipelineVersionId(),
                lower(build.getStatus() == null ? null : build.getStatus().name()),
                lower(build.getStage() == null ? null : build.getStage().name()),
                lower(build.getTriggerType() == null ? null : build.getTriggerType().name()),
                build.getErrorCode(),
                build.getErrorMessage(),
                build.getStartedAt(),
                build.getFinishedAt(),
                rows.stream().map(output -> toOutput(output, datasetIds, versionIds)).toList()
        );
    }

    private Map<UUID, UUID> datasetsByOutputId(List<PipelineBuildOutputEntity> rows) {
        Set<UUID> outputIds = rows.stream()
                .map(PipelineBuildOutputEntity::getOutputId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (outputIds.isEmpty()) {
            return Map.of();
        }
        return datasets.findByOutputIdIn(outputIds).stream()
                .collect(Collectors.toMap(
                        PipelineDatasetEntity::getOutputId,
                        PipelineDatasetEntity::getId,
                        (left, right) -> left
                ));
    }

    private Map<UUID, UUID> versionsByBuildOutputId(List<PipelineBuildOutputEntity> rows) {
        Set<UUID> rowIds = rows.stream()
                .map(PipelineBuildOutputEntity::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (rowIds.isEmpty()) {
            return Map.of();
        }
        return datasetVersions.findByBuildOutputIdIn(rowIds).stream()
                .collect(Collectors.toMap(
                        PipelineDatasetVersionEntity::getBuildOutputId,
                        PipelineDatasetVersionEntity::getId,
                        (left, right) -> left
                ));
    }

    private BuildOutputResponse toOutput(
            PipelineBuildOutputEntity output,
            Map<UUID, UUID> datasetIds,
            Map<UUID, UUID> versionIds
    ) {
        return new BuildOutputResponse(
                output.getId(),
                output.getOutputId(),
                datasetIds.get(output.getOutputId()),
                versionIds.get(output.getId()),
                lower(output.getOutputType() == null ? null : output.getOutputType().name()),
                lower(output.getStatus() == null ? null : output.getStatus().name()),
                lower(output.getStage() == null ? null : output.getStage().name()),
                output.getEngineJobId(),
                output.getErrorCode(),
                output.getErrorMessage(),
                output.getResult(),
                output.getStats(),
                output.getStartedAt(),
                output.getFinishedAt()
        );
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
