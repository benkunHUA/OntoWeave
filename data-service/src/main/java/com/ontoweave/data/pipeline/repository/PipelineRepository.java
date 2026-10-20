package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {

    @Query(value = "SELECT * FROM data.pipeline WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<PipelineEntity> lockById(@Param("id") UUID id);
}
