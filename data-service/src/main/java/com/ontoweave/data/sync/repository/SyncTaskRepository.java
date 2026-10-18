package com.ontoweave.data.sync.repository;

import com.ontoweave.data.sync.domain.SyncTaskEntity;
import com.ontoweave.data.sync.domain.SyncTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncTaskRepository extends JpaRepository<SyncTaskEntity, UUID> {

    List<SyncTaskEntity> findByConnectionIdOrderByCreatedAtAsc(UUID connectionId);

    List<SyncTaskEntity> findByConnectionIdAndStatusOrderByCreatedAtAsc(
            UUID connectionId,
            SyncTaskStatus status
    );

    Optional<SyncTaskEntity> findByConnectionIdAndSchemaNameAndTableName(
            UUID connectionId,
            String schemaName,
            String tableName
    );

    Optional<SyncTaskEntity> findByIdAndConnectionId(UUID id, UUID connectionId);
}
