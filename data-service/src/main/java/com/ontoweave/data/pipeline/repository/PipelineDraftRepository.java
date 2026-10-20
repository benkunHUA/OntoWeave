package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PipelineDraftRepository extends JpaRepository<PipelineDraftEntity, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE data.pipeline_draft
            SET definition = CAST(:definition AS jsonb), revision = revision + 1,
                updated_by_type = :actorType, updated_by_id = :actorId, updated_at = now()
            WHERE pipeline_id = :pipelineId AND revision = :baseRevision
            """, nativeQuery = true)
    int updateDefinition(
            @Param("pipelineId") UUID pipelineId,
            @Param("baseRevision") long baseRevision,
            @Param("definition") String definition,
            @Param("actorType") String actorType,
            @Param("actorId") UUID actorId
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE data.pipeline_draft
            SET definition = CAST(:definition AS jsonb),
                updated_by_type = :actorType, updated_by_id = :actorId, updated_at = now()
            WHERE pipeline_id = :pipelineId
            """, nativeQuery = true)
    int updateDefinitionWithoutRevision(
            @Param("pipelineId") UUID pipelineId,
            @Param("definition") String definition,
            @Param("actorType") String actorType,
            @Param("actorId") UUID actorId
    );
}
