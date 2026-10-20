package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PipelineDraftRepository extends JpaRepository<PipelineDraftEntity, UUID> {
}
