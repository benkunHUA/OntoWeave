package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class PipelineBuildMapper {

    public BuildResponse toResponse(PipelineBuildEntity build, List<PipelineBuildOutputEntity> outputs) {
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
                outputs == null ? List.of() : outputs.stream().map(this::toOutput).toList()
        );
    }

    private BuildOutputResponse toOutput(PipelineBuildOutputEntity output) {
        return new BuildOutputResponse(
                output.getId(),
                output.getOutputId(),
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
