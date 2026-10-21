package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStage;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineBuildRepository extends JpaRepository<PipelineBuildEntity, UUID> {

    boolean existsByPipelineIdAndStatusIn(UUID pipelineId, List<PipelineBuildStatus> statuses);

    Optional<PipelineBuildEntity> findTopByPipelineIdOrderByCreatedAtDesc(UUID pipelineId);

    Optional<PipelineBuildEntity> findByIdAndPipelineId(UUID id, UUID pipelineId);

    Optional<PipelineBuildEntity> findByPipelineIdAndOperationId(UUID pipelineId, UUID operationId);

    @Query(value = "SELECT * FROM data.pipeline_build WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<PipelineBuildEntity> lockById(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            update PipelineBuildEntity b
            set b.stage = :stage, b.updatedAt = CURRENT_TIMESTAMP
            where b.id = :id and b.status = :status
            """)
    int updateStage(
            @Param("id") UUID id,
            @Param("stage") PipelineBuildStage stage,
            @Param("status") PipelineBuildStatus status
    );

    @Query("""
            select b from PipelineBuildEntity b
            where b.status = :status
              and (b.heartbeatAt is null or b.heartbeatAt < :cutoff)
            """)
    List<PipelineBuildEntity> findStaleRunning(
            @Param("status") PipelineBuildStatus status,
            @Param("cutoff") Instant cutoff
    );

    @Query(value = """
            SELECT * FROM data.pipeline_build
            WHERE status = 'queued'
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<PipelineBuildEntity> lockQueued(@Param("limit") int limit);
}
