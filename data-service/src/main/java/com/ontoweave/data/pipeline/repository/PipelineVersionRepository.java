package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PipelineVersionRepository extends JpaRepository<PipelineVersionEntity, UUID> {

    Optional<PipelineVersionEntity> findByIdAndPipelineId(UUID id, UUID pipelineId);

    Optional<PipelineVersionEntity> findByPipelineIdAndDefinitionHash(UUID pipelineId, String definitionHash);

    Optional<PipelineVersionEntity> findTopByPipelineIdOrderByVersionNumberDesc(UUID pipelineId);
}
