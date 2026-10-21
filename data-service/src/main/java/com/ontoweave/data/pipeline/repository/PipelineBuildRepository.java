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

    Optional<PipelineBuildEntity>
            findFirstByPipelineIdAndPipelineVersionIdAndStatusInOrderByCreatedAtDescIdDesc(
                    UUID pipelineId,
                    UUID pipelineVersionId,
                    List<PipelineBuildStatus> statuses
            );

    @Query(value = "SELECT * FROM data.pipeline_build WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<PipelineBuildEntity> lockById(@Param("id") UUID id);

    @Query(value = """
            SELECT * FROM data.pipeline_build
            WHERE id = :id AND pipeline_id = :pipelineId
            FOR UPDATE
            """, nativeQuery = true)
    Optional<PipelineBuildEntity> lockByIdAndPipelineId(
            @Param("id") UUID id,
            @Param("pipelineId") UUID pipelineId
    );

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

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update PipelineBuildEntity b
            set b.logUri = :logUri, b.updatedAt = CURRENT_TIMESTAMP
            where b.id = :id and b.logUri is null
            """)
    int updateLogUriIfAbsent(@Param("id") UUID id, @Param("logUri") String logUri);

    @Query(value = """
            SELECT * FROM data.pipeline_build
            WHERE status = 'running'
              AND (heartbeat_at IS NULL OR heartbeat_at < :cutoff)
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<PipelineBuildEntity> lockStaleRunning(
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit
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
