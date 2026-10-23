package com.ontoweave.data.ontology.repository;

import com.ontoweave.data.ontology.domain.OntologyRelationVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OntologyRelationVersionRepository
        extends JpaRepository<OntologyRelationVersionEntity, UUID> {

    List<OntologyRelationVersionEntity> findByBindingIdOrderByCreatedAtDesc(UUID bindingId);

    List<OntologyRelationVersionEntity> findByRelationIdOrderByCreatedAtDesc(UUID relationId);

    List<OntologyRelationVersionEntity> findByRunId(UUID runId);

    @Query(value = """
            SELECT * FROM data.ontology_relation_version
            WHERE status = 'RETIRED'
              AND retired_at <= :cutoff
              AND cleanup_completed_at IS NULL
            ORDER BY retired_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<OntologyRelationVersionEntity> lockCleanable(
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_relation_version
            SET status = 'ACTIVE', activated_at = :activatedAt
            WHERE id = :id
            """, nativeQuery = true)
    int markActive(@Param("id") UUID id, @Param("activatedAt") Instant activatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_relation_version
            SET status = 'RETIRED', retired_at = :retiredAt
            WHERE id = :id AND status = 'ACTIVE'
            """, nativeQuery = true)
    int markRetired(@Param("id") UUID id, @Param("retiredAt") Instant retiredAt);
}
