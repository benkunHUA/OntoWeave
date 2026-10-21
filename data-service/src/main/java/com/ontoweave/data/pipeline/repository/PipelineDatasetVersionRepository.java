package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineDatasetVersionRepository extends JpaRepository<PipelineDatasetVersionEntity, UUID> {

    Optional<PipelineDatasetVersionEntity> findByIdAndDatasetId(UUID id, UUID datasetId);

    List<PipelineDatasetVersionEntity> findByIdIn(Collection<UUID> ids);

    Optional<PipelineDatasetVersionEntity> findByBuildOutputId(UUID buildOutputId);

    List<PipelineDatasetVersionEntity> findByBuildOutputIdIn(Collection<UUID> buildOutputIds);

    Optional<PipelineDatasetVersionEntity> findTopByDatasetIdOrderByVersionNumberDesc(UUID datasetId);

}
