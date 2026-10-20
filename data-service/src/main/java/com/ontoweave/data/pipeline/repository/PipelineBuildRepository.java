package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PipelineBuildRepository extends JpaRepository<PipelineBuildEntity, UUID> {

    @Query(value = """
            SELECT * FROM data.pipeline_build
            WHERE status = 'queued'
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<PipelineBuildEntity> lockQueued(@Param("limit") int limit);
}
