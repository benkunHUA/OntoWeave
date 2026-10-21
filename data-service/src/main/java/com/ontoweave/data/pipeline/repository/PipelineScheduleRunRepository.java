package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineScheduleRunRepository extends JpaRepository<PipelineScheduleRunEntity, UUID> {

    Optional<PipelineScheduleRunEntity> findByScheduleIdAndScheduledFor(
            UUID scheduleId,
            Instant scheduledFor
    );

    Optional<PipelineScheduleRunEntity> findFirstByScheduleIdAndStatusOrderByScheduledForAsc(
            UUID scheduleId,
            PipelineScheduleRunStatus status
    );

    @Query(value = """
            SELECT * FROM data.pipeline_schedule_run
            WHERE schedule_id = :scheduleId AND status = 'pending'
            FOR UPDATE
            """, nativeQuery = true)
    List<PipelineScheduleRunEntity> lockPendingByScheduleId(
            @Param("scheduleId") UUID scheduleId
    );

    @Query(value = """
            SELECT * FROM data.pipeline_schedule_run
            WHERE pipeline_id = :pipelineId
            ORDER BY scheduled_for DESC, id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<PipelineScheduleRunEntity> findPage(
            @Param("pipelineId") UUID pipelineId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}
