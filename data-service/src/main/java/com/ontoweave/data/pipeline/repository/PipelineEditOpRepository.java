package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineEditOpEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineEditOpRepository extends JpaRepository<PipelineEditOpEntity, UUID> {
}
