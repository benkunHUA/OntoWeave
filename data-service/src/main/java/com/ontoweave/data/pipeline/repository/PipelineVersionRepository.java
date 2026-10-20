package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineVersionRepository extends JpaRepository<PipelineVersionEntity, UUID> {
}
