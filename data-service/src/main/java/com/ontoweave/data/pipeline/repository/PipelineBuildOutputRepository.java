package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface PipelineBuildOutputRepository extends JpaRepository<PipelineBuildOutputEntity, UUID> {

    List<PipelineBuildOutputEntity> findByBuildIdOrderByCreatedAtAsc(UUID buildId);

    Optional<PipelineBuildOutputEntity> findByBuildIdAndOutputId(UUID buildId, UUID outputId);
}
