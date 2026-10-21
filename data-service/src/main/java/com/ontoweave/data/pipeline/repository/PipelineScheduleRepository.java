package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineScheduleRepository extends JpaRepository<PipelineScheduleEntity, UUID> {

    Optional<PipelineScheduleEntity> findByPipelineId(UUID pipelineId);

    @Query(value = "SELECT * FROM data.pipeline_schedule WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<PipelineScheduleEntity> lockById(@Param("id") UUID id);

    @Query(value = """
            SELECT * FROM data.pipeline_schedule
            WHERE pipeline_id = :pipelineId
            FOR UPDATE
            """, nativeQuery = true)
    Optional<PipelineScheduleEntity> lockByPipelineId(@Param("pipelineId") UUID pipelineId);

    @Query(value = """
            SELECT * FROM data.pipeline_schedule
            WHERE status = 'active' AND next_run_at <= :now
            ORDER BY next_run_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<PipelineScheduleEntity> lockDue(
            @Param("now") Instant now,
            @Param("limit") int limit
    );
}
