package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineDatasetRepository extends JpaRepository<PipelineDatasetEntity, UUID> {
}
