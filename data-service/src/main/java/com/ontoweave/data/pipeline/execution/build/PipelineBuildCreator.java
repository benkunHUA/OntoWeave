package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PipelineBuildCreator {

    private static final List<PipelineBuildStatus> ACTIVE_STATUSES = List.of(
            PipelineBuildStatus.QUEUED, PipelineBuildStatus.RUNNING
    );

    private final PipelineRepository pipelines;
    private final PipelineBuildRepository builds;
    private final PipelineBuildOutputRepository outputs;
    private final ObjectMapper json;

    public PipelineBuildCreator(
            PipelineRepository pipelines,
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository outputs,
            ObjectMapper json
    ) {
        this.pipelines = pipelines;
        this.builds = builds;
        this.outputs = outputs;
        this.json = json;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PipelineBuildEntity create(PipelineBuildEntity candidate, PipelineVersionEntity version) {
        return createInternal(candidate, version);
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            noRollbackFor = ApiException.class
    )
    public PipelineBuildEntity createInCurrentTransaction(
            PipelineBuildEntity candidate,
            PipelineVersionEntity version
    ) {
        return createInternal(candidate, version);
    }

    private PipelineBuildEntity createInternal(
            PipelineBuildEntity candidate,
            PipelineVersionEntity version
    ) {
        pipelines.lockById(candidate.getPipelineId())
                .orElseThrow(() -> notFound("流程不存在"));
        Optional<PipelineBuildEntity> existing = builds.findByPipelineIdAndOperationId(
                candidate.getPipelineId(), candidate.getOperationId()
        );
        if (existing.isPresent()) {
            return existing.get();
        }
        if (builds.existsByPipelineIdAndStatusIn(candidate.getPipelineId(), ACTIVE_STATUSES)) {
            throw activeBuild();
        }
        builds.saveAndFlush(candidate);
        writeOutputs(candidate.getId(), version.getOutputs());
        return candidate;
    }

    private void writeOutputs(UUID buildId, JsonNode frozenOutputs) {
        List<PipelineBuildOutputEntity> rows = new ArrayList<>();
        for (PipelineFrozenOutput output : PipelineFrozenOutput.from(frozenOutputs, json)) {
            if (output.outputId() == null) {
                continue;
            }
            rows.add(new PipelineBuildOutputEntity(
                    UUID.randomUUID(),
                    buildId,
                    output.outputId(),
                    output.outputType() == null ? PipelineOutputType.DATASET : output.outputType(),
                    PipelineOutputStatus.QUEUED,
                    json.createObjectNode(),
                    json.createObjectNode()
            ));
        }
        if (!rows.isEmpty()) {
            outputs.saveAllAndFlush(rows);
        }
    }

    private ApiException activeBuild() {
        return new ApiException(
                HttpStatus.CONFLICT,
                PipelineBuildConstants.CODE_ACTIVE,
                "流程已有排队或运行中的构建"
        );
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, PipelineBuildConstants.CODE_NOT_FOUND, message);
    }
}
