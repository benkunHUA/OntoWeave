package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineOutputRepository extends JpaRepository<PipelineOutputEntity, UUID> {
}
