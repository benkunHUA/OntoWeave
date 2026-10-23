package com.ontoweave.data.ontology.repository;

import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OntologyIndexRunRepository
        extends JpaRepository<OntologyIndexRunEntity, UUID>, OntologyIndexRunRepositoryCustom {

    List<OntologyIndexRunEntity> findBySubjectKindAndSubjectIdOrderByRequestedAtDesc(
            SubjectKind subjectKind,
            UUID subjectId
    );

    List<OntologyIndexRunEntity> findByBindingIdOrderByRequestedAtDesc(UUID bindingId);

    @Query(value = """
            SELECT * FROM data.ontology_index_run
            WHERE status = 'QUEUED'
            ORDER BY requested_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<OntologyIndexRunEntity> lockNextQueued();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_index_run
            SET status = 'RUNNING', phase = 'READING', started_at = now(),
                heartbeat_at = now(), lease_owner = :worker,
                lease_expires_at = now() + interval '60 seconds'
            WHERE id = :id AND status = 'QUEUED'
            """, nativeQuery = true)
    int markRunning(@Param("id") UUID id, @Param("worker") String worker);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_index_run
            SET phase = :#{#phase.name()}, heartbeat_at = now(),
                lease_expires_at = :leaseExpiresAt
            WHERE id = :id AND status = 'RUNNING' AND lease_owner = :worker
            """, nativeQuery = true)
    int heartbeat(
            @Param("id") UUID id,
            @Param("worker") String worker,
            @Param("phase") com.ontoweave.data.ontology.common.RunPhase phase,
            @Param("leaseExpiresAt") Instant leaseExpiresAt
    );

    @Query(value = """
            SELECT * FROM data.ontology_index_run
            WHERE status = 'RUNNING' AND lease_expires_at < :now
            ORDER BY lease_expires_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<OntologyIndexRunEntity> findExpiredLeases(
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_index_run
            SET cancel_requested_at = now()
            WHERE id = :id AND status = 'RUNNING' AND cancel_requested_at IS NULL
            """, nativeQuery = true)
    int requestCancel(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_index_run
            SET status = 'CANCELLED', phase = NULL, finished_at = now(),
                lease_owner = NULL, lease_expires_at = NULL
            WHERE id = :id AND status = 'QUEUED'
            """, nativeQuery = true)
    int cancelQueued(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_index_run
            SET status = :status, phase = NULL, finished_at = :finishedAt,
                error_code = :errorCode, error_message = :errorMessage,
                error_details = CAST(:errorDetails AS jsonb),
                stats = CAST(:stats AS jsonb),
                lease_owner = NULL, lease_expires_at = NULL
            WHERE id = :id
            """, nativeQuery = true)
    int markTerminalStatus(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("errorDetails") String errorDetails,
            @Param("stats") String stats,
            @Param("finishedAt") Instant finishedAt
    );

    default int markTerminal(
            UUID id,
            RunStatus status,
            String errorCode,
            String errorMessage,
            String errorDetails,
            String stats,
            Instant finishedAt
    ) {
        return markTerminalStatus(
                id,
                status.name(),
                errorCode,
                errorMessage,
                errorDetails == null ? "null" : errorDetails,
                stats == null ? "{}" : stats,
                finishedAt
        );
    }
}
