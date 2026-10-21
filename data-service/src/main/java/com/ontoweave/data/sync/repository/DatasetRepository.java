package com.ontoweave.data.sync.repository;

import com.ontoweave.data.sync.domain.DatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

import jakarta.persistence.LockModeType;

public interface DatasetRepository extends JpaRepository<DatasetEntity, UUID> {

    List<DatasetEntity> findByConnectionIdOrderByCreatedAtAsc(UUID connectionId);

    Optional<DatasetEntity> findByIdAndConnectionId(UUID id, UUID connectionId);

    List<DatasetEntity> findByIdIn(Collection<UUID> ids);

    @Query(value = """
            SELECT d.* FROM data.dataset d
            JOIN data.data_connection c ON c.id = d.connection_id
            WHERE c.owner_id = :ownerId AND d.sync_task_id IS NOT NULL
            ORDER BY d.created_at ASC, d.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DatasetEntity> findPageByOwnerId(
            @Param("ownerId") UUID ownerId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT count(*) FROM data.dataset d
            JOIN data.data_connection c ON c.id = d.connection_id
            WHERE c.owner_id = :ownerId AND d.sync_task_id IS NOT NULL
            """, nativeQuery = true)
    long countByOwnerId(@Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DatasetEntity d where d.id = :id")
    Optional<DatasetEntity> findForUpdate(@Param("id") UUID id);
}
