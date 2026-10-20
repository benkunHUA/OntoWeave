package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineDatasetVersionRepository extends JpaRepository<PipelineDatasetVersionEntity, UUID> {
}
