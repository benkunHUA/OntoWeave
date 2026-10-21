package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineOutputRepository extends JpaRepository<PipelineOutputEntity, UUID> {

    List<PipelineOutputEntity> findByPipelineIdAndArchivedAtIsNull(UUID pipelineId);

    Optional<PipelineOutputEntity> findByPipelineIdAndNodeId(UUID pipelineId, UUID nodeId);

    Optional<PipelineOutputEntity> findByPipelineIdAndName(UUID pipelineId, String name);

}
