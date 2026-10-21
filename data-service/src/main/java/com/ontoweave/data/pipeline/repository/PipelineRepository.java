package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {

    Optional<PipelineEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<PipelineEntity> findByIdInAndOwnerId(Collection<UUID> ids, UUID ownerId);

    Optional<PipelineEntity> findByConversationId(UUID conversationId);

    @Query(value = """
            SELECT * FROM data.pipeline
            WHERE owner_id = :ownerId AND archived_at IS NULL
            ORDER BY updated_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<PipelineEntity> findActivePage(
            @Param("ownerId") UUID ownerId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("""
            select count(p) from PipelineEntity p
            where p.ownerId = :ownerId and p.archivedAt is null
            """)
    long countActive(@Param("ownerId") UUID ownerId);

    @Modifying
    @Query(value = """
            INSERT INTO data.pipeline
                (id, owner_id, conversation_id, name, status, created_at, updated_at)
            VALUES (:id, :ownerId, :conversationId, :name, 'draft', now(), now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("ownerId") UUID ownerId,
            @Param("conversationId") UUID conversationId,
            @Param("name") String name
    );

    @Query(value = "SELECT * FROM data.pipeline WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<PipelineEntity> lockById(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE data.pipeline
            SET current_version_id = :versionId, status = 'active', updated_at = now()
            WHERE id = :pipelineId
            """, nativeQuery = true)
    int activateVersion(@Param("pipelineId") UUID pipelineId, @Param("versionId") UUID versionId);
}
