package com.ontoweave.data.pipeline.repository;

import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineDatasetRepository extends JpaRepository<PipelineDatasetEntity, UUID> {

    Optional<PipelineDatasetEntity> findByOutputId(UUID outputId);

    List<PipelineDatasetEntity> findByOutputIdIn(Collection<UUID> outputIds);

    List<PipelineDatasetEntity> findByOutputIdInAndArchivedAtIsNull(Collection<UUID> outputIds);

    Optional<PipelineDatasetEntity> findByIdAndPipelineId(UUID id, UUID pipelineId);

    List<PipelineDatasetEntity> findByIdIn(Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PipelineDatasetEntity d
            set d.currentVersionId = :versionId, d.updatedAt = CURRENT_TIMESTAMP
            where d.id = :datasetId
            """)
    int activateVersion(@Param("datasetId") UUID datasetId, @Param("versionId") UUID versionId);
}
