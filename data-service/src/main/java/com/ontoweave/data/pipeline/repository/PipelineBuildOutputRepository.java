package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineBuildOutputRepository extends JpaRepository<PipelineBuildOutputEntity, UUID> {
}
