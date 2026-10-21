package com.ontoweave.data.sync.repository;

import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersionEntity, UUID> {

    Optional<DatasetVersionEntity> findTopByDatasetIdOrderByVersionNumberDesc(UUID datasetId);

    Optional<DatasetVersionEntity> findByIdAndDatasetId(UUID id, UUID datasetId);

    List<DatasetVersionEntity> findByIdIn(Collection<UUID> ids);

    Optional<DatasetVersionEntity> findByDatasetIdAndIcebergTableAndIcebergSnapshotId(
            UUID datasetId,
            String icebergTable,
            long icebergSnapshotId
    );

    boolean existsByIcebergCatalogAndIcebergNamespaceAndIcebergTable(
            String icebergCatalog,
            String icebergNamespace,
            String icebergTable
    );

    List<DatasetVersionEntity> findByDatasetIdOrderByVersionNumberDesc(UUID datasetId);

    @Query(value = """
            SELECT * FROM data.dataset_version
            WHERE dataset_id = :datasetId
            ORDER BY version_number DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DatasetVersionEntity> findPageByDatasetId(
            @Param("datasetId") UUID datasetId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("select count(v) from DatasetVersionEntity v where v.datasetId = :datasetId")
    long countByDatasetId(@Param("datasetId") UUID datasetId);
}
