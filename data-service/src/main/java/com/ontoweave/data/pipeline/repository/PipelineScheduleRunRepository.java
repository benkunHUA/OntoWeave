package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineScheduleRunRepository extends JpaRepository<PipelineScheduleRunEntity, UUID> {
}
