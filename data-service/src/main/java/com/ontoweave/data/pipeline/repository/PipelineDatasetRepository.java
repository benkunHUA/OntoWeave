package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineDatasetRepository extends JpaRepository<PipelineDatasetEntity, UUID> {

    Optional<PipelineDatasetEntity> findByOutputId(UUID outputId);

    List<PipelineDatasetEntity> findByOutputIdIn(Collection<UUID> outputIds);
}
