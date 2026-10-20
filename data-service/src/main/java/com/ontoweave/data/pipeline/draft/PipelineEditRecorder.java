package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.domain.PipelineEditOpEntity;
import com.ontoweave.data.pipeline.repository.PipelineEditOpRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PipelineEditRecorder {

    private static final int FIRST_SEQUENCE = 1;

    private final PipelineEditOpRepository repository;

    public PipelineEditRecorder(PipelineEditOpRepository repository) {
        this.repository = repository;
    }

    public void recordSemanticEdit(SemanticEdit edit) {
        repository.save(new PipelineEditOpEntity(
                UUID.randomUUID(),
                edit.pipelineId(),
                edit.revision(),
                FIRST_SEQUENCE,
                edit.opType(),
                edit.targetType(),
                edit.targetId(),
                edit.before(),
                edit.after(),
                edit.actorType(),
                edit.actorId(),
                edit.operationId(),
                edit.turnId()
        ));
    }

    public Optional<PipelineEditOpEntity> findLatest(UUID pipelineId) {
        return repository.findTopByPipelineIdOrderByRevisionDescSeqDesc(pipelineId);
    }

    public List<PipelineEditOpEntity> findTurn(UUID pipelineId, UUID turnId) {
        return repository.findByPipelineIdAndTurnIdOrderByRevisionAscSeqAsc(pipelineId, turnId);
    }

    public record SemanticEdit(
            UUID pipelineId,
            long revision,
            String opType,
            String targetType,
            UUID targetId,
            JsonNode before,
            JsonNode after,
            String actorType,
            UUID actorId,
            UUID operationId,
            UUID turnId
    ) {
    }
}
