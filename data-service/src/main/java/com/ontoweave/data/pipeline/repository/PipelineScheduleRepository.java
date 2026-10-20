package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineScheduleRepository extends JpaRepository<PipelineScheduleEntity, UUID> {
}
