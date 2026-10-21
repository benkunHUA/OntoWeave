package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PipelineDraftRepository extends JpaRepository<PipelineDraftEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PipelineDraftEntity d where d.pipelineId = :pipelineId")
    Optional<PipelineDraftEntity> findByIdForUpdate(@Param("pipelineId") UUID pipelineId);

    @Modifying
    @Query(value = """
            INSERT INTO data.pipeline_draft
                (pipeline_id, definition, revision, updated_by_type, updated_by_id, updated_at)
            VALUES (:pipelineId, CAST(:definition AS jsonb), 0, 'user', :ownerId, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("pipelineId") UUID pipelineId,
            @Param("definition") String definition,
            @Param("ownerId") UUID ownerId
    );

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
