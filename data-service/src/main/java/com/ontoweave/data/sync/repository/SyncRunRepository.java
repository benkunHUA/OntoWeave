package com.ontoweave.data.sync.repository;

import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRunEntity, UUID> {

    Optional<SyncRunEntity> findBySyncTaskIdAndOperationId(UUID syncTaskId, UUID operationId);

    Optional<SyncRunEntity> findByFlinkJobName(String flinkJobName);

    List<SyncRunEntity> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId);

    @Query("""
            select r from SyncRunEntity r, SyncTaskEntity t
            where r.syncTaskId = t.id and t.connectionId = :connectionId
            order by r.createdAt desc
            """)
    List<SyncRunEntity> findRecentByConnectionId(@Param("connectionId") UUID connectionId);

    List<SyncRunEntity> findTop50ByStatusInOrderByCreatedAtAsc(Collection<SyncRunStatus> statuses);

    List<SyncRunEntity> findTop20ByStatusAndDispatchedAtIsNullAndDispatchAttemptsLessThanOrderByCreatedAtAsc(
            SyncRunStatus status,
            int maxAttempts
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.status = :target, r.updatedAt = :now
            where r.id = :id and r.status = :expected
            """)
    int claim(
            @Param("id") UUID id,
            @Param("expected") SyncRunStatus expected,
            @Param("target") SyncRunStatus target,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.dispatchAttempts = r.dispatchAttempts + 1
            where r.id = :id and r.dispatchedAt is null and r.dispatchAttempts < :maxAttempts
            """)
    int beginDispatch(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.dispatchedAt = :dispatchedAt
            where r.id = :id and r.dispatchedAt is null
            """)
    int confirmDispatched(@Param("id") UUID id, @Param("dispatchedAt") Instant dispatchedAt);
}
