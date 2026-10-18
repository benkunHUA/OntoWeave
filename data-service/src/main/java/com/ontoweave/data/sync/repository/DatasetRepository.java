package com.ontoweave.data.sync.repository;

import com.ontoweave.data.sync.domain.DatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

public interface DatasetRepository extends JpaRepository<DatasetEntity, UUID> {

    List<DatasetEntity> findByConnectionIdOrderByCreatedAtAsc(UUID connectionId);

    Optional<DatasetEntity> findByIdAndConnectionId(UUID id, UUID connectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DatasetEntity d where d.id = :id")
    Optional<DatasetEntity> findForUpdate(@Param("id") UUID id);
}
