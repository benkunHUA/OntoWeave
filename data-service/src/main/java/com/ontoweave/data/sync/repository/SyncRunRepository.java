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

    Optional<SyncRunEntity> findFirstBySyncTaskIdAndStatusInOrderByCreatedAtDesc(
            UUID syncTaskId,
            Collection<SyncRunStatus> statuses
    );

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
            set r.status = :target, r.flinkJobState = :flinkJobState, r.updatedAt = :now
            where r.id = :id and r.status = :expected
            """)
    int markRunning(
            @Param("id") UUID id,
            @Param("expected") SyncRunStatus expected,
            @Param("target") SyncRunStatus target,
            @Param("flinkJobState") String flinkJobState,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.flinkJobState = :flinkJobState,
                r.lastCheckpointAt = coalesce(:checkpointAt, r.lastCheckpointAt),
                r.lastSnapshotId = coalesce(:snapshotId, r.lastSnapshotId),
                r.snapshotCompletedAt = coalesce(:snapshotCompletedAt, r.snapshotCompletedAt),
                r.nextVersionDueAt = coalesce(:nextVersionDueAt, r.nextVersionDueAt),
                r.updatedAt = :now
            where r.id = :id and r.status in :expected
            """)
    int recordProgress(
            @Param("id") UUID id,
            @Param("expected") Collection<SyncRunStatus> expected,
            @Param("flinkJobState") String flinkJobState,
            @Param("checkpointAt") Instant checkpointAt,
            @Param("snapshotId") Long snapshotId,
            @Param("snapshotCompletedAt") Instant snapshotCompletedAt,
            @Param("nextVersionDueAt") Instant nextVersionDueAt,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.status = :target, r.updatedAt = :now
            where r.id = :id and r.status in :expected and r.status <> :target
            """)
    int requestStop(
            @Param("id") UUID id,
            @Param("expected") Collection<SyncRunStatus> expected,
            @Param("target") SyncRunStatus target,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.stopRequestedAt = :now, r.updatedAt = :now
            where r.id = :id and r.status = :status and r.stopRequestedAt is null
            """)
    int claimStopRequest(
            @Param("id") UUID id,
            @Param("status") SyncRunStatus status,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.stopRequestedAt = null, r.updatedAt = :now
            where r.id = :id and r.status = :status and r.stopRequestedAt = :claimedAt
            """)
    int releaseStopRequest(
            @Param("id") UUID id,
            @Param("status") SyncRunStatus status,
            @Param("claimedAt") Instant claimedAt,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.stopRequestedAt = :now, r.updatedAt = :now
            where r.id = :id and r.status = :status and r.stopRequestedAt = :claimedAt
            """)
    int claimCancelFallback(
            @Param("id") UUID id,
            @Param("status") SyncRunStatus status,
            @Param("claimedAt") Instant claimedAt,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.snapshotExpectedRows = :snapshotExpectedRows,
                r.serverIdStart = :serverIdStart,
                r.serverIdEnd = :serverIdEnd,
                r.updatedAt = :now
            where r.id = :id and r.status = :expected
            """)
    int recordPrepared(
            @Param("id") UUID id,
            @Param("snapshotExpectedRows") Long snapshotExpectedRows,
            @Param("serverIdStart") Long serverIdStart,
            @Param("serverIdEnd") Long serverIdEnd,
            @Param("expected") SyncRunStatus expected,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.status = :target, r.flinkJobId = :jobId, r.startedAt = :now, r.updatedAt = :now
            where r.id = :id and r.status = :expected
            """)
    int markSubmitted(
            @Param("id") UUID id,
            @Param("jobId") String jobId,
            @Param("expected") SyncRunStatus expected,
            @Param("target") SyncRunStatus target,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.flinkJobId = :jobId, r.startedAt = :now, r.updatedAt = :now
            where r.id = :id and r.status = :expected
            """)
    int attachSubmittedJob(
            @Param("id") UUID id,
            @Param("jobId") String jobId,
            @Param("expected") SyncRunStatus expected,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.status = :target,
                r.flinkJobState = coalesce(:flinkJobState, r.flinkJobState),
                r.errorCode = :errorCode,
                r.errorMessage = :errorMessage,
                r.finishedAt = :now,
                r.updatedAt = :now
            where r.id = :id and r.status = :expected
            """)
    int markFailed(
            @Param("id") UUID id,
            @Param("expected") SyncRunStatus expected,
            @Param("target") SyncRunStatus target,
            @Param("flinkJobState") String flinkJobState,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.status = :target,
                r.flinkJobState = :flinkJobState,
                r.lastCheckpointAt = coalesce(:checkpointAt, r.lastCheckpointAt),
                r.lastSnapshotId = coalesce(:snapshotId, r.lastSnapshotId),
                r.finishedAt = :now,
                r.updatedAt = :now
            where r.id = :id and r.status in :expected
            """)
    int markSucceeded(
            @Param("id") UUID id,
            @Param("expected") Collection<SyncRunStatus> expected,
            @Param("target") SyncRunStatus target,
            @Param("flinkJobState") String flinkJobState,
            @Param("checkpointAt") Instant checkpointAt,
            @Param("snapshotId") Long snapshotId,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.status = :target,
                r.flinkJobState = coalesce(:flinkJobState, r.flinkJobState),
                r.savepointPath = :savepointPath,
                r.lastCheckpointAt = coalesce(:checkpointAt, r.lastCheckpointAt),
                r.lastSnapshotId = coalesce(:snapshotId, r.lastSnapshotId),
                r.finishedAt = :now,
                r.updatedAt = :now
            where r.id = :id
              and r.status = :expected
              and ((:jobId is null and r.flinkJobId is null) or r.flinkJobId = :jobId)
            """)
    int markStopped(
            @Param("id") UUID id,
            @Param("expected") SyncRunStatus expected,
            @Param("target") SyncRunStatus target,
            @Param("jobId") String jobId,
            @Param("flinkJobState") String flinkJobState,
            @Param("savepointPath") String savepointPath,
            @Param("checkpointAt") Instant checkpointAt,
            @Param("snapshotId") Long snapshotId,
            @Param("now") Instant now
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
            set r.status = :target,
                r.dispatchedAt = null,
                r.flinkJobId = null,
                r.flinkJobState = null,
                r.startedAt = null,
                r.updatedAt = :now
            where r.id = :id and r.status = :expected
            """)
    int requeuePreparing(
            @Param("id") UUID id,
            @Param("expected") SyncRunStatus expected,
            @Param("target") SyncRunStatus target,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.nextVersionDueAt = :nextVersionDueAt, r.updatedAt = :now
            where r.id = :id and r.status = :expected
            """)
    int scheduleNextVersion(
            @Param("id") UUID id,
            @Param("expected") SyncRunStatus expected,
            @Param("nextVersionDueAt") Instant nextVersionDueAt,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
            update SyncRunEntity r
            set r.lastPublishedSnapshotId = :snapshotId,
                r.nextVersionDueAt = :nextVersionDueAt,
                r.updatedAt = :now
            where r.id = :id
            """)
    int markSnapshotPublished(
            @Param("id") UUID id,
            @Param("snapshotId") long snapshotId,
            @Param("nextVersionDueAt") Instant nextVersionDueAt,
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
