package com.ontoweave.data.sync.repository;

import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersionEntity, UUID> {

    Optional<DatasetVersionEntity> findTopByDatasetIdOrderByVersionNumberDesc(UUID datasetId);

    Optional<DatasetVersionEntity> findByIdAndDatasetId(UUID id, UUID datasetId);

    Optional<DatasetVersionEntity> findByDatasetIdAndIcebergTableAndIcebergSnapshotId(
            UUID datasetId,
            String icebergTable,
            long icebergSnapshotId
    );

    List<DatasetVersionEntity> findByDatasetIdOrderByVersionNumberDesc(UUID datasetId);
}
