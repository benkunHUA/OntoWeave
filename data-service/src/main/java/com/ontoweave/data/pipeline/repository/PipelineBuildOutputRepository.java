package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface PipelineBuildOutputRepository extends JpaRepository<PipelineBuildOutputEntity, UUID> {

    List<PipelineBuildOutputEntity> findByBuildIdOrderByCreatedAtAsc(UUID buildId);

    Optional<PipelineBuildOutputEntity> findByBuildIdAndOutputId(UUID buildId, UUID outputId);

    @Query("""
            select o.logUri from PipelineBuildOutputEntity o
            where o.buildId = :buildId and o.outputId = :outputId
            """)
    Optional<String> findLogUriByBuildIdAndOutputId(
            @Param("buildId") UUID buildId,
            @Param("outputId") UUID outputId
    );

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("""
            update PipelineBuildOutputEntity o
            set o.engineJobId = :engineJobId,
                o.logUri = coalesce(:logUri, o.logUri),
                o.updatedAt = CURRENT_TIMESTAMP
            where o.buildId = :buildId and o.outputId = :outputId
            """)
    int updateEngineJobAndLogUri(
            @Param("buildId") UUID buildId,
            @Param("outputId") UUID outputId,
            @Param("engineJobId") String engineJobId,
            @Param("logUri") String logUri
    );
}
