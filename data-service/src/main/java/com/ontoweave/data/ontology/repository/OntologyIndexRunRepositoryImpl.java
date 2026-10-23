package com.ontoweave.data.ontology.repository;

import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class OntologyIndexRunRepositoryImpl implements OntologyIndexRunRepositoryCustom {

    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    public OntologyIndexRunRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public OntologyIndexRunEntity upsertQueued(OntologyIndexRunEntity candidate) {
        String mapping = candidate.getMappingSnapshot() == null
                ? "{}"
                : candidate.getMappingSnapshot().toString();
        UUID id = jdbc.queryForObject("""
                INSERT INTO data.ontology_index_run
                    (id, owner_id, subject_kind, subject_id, binding_id,
                     target_dataset_kind, target_dataset_version_id, mapping_snapshot,
                     mapping_revision, trigger_type, status, requested_at, attempt,
                     parent_run_id)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, 'QUEUED', now(), ?, ?)
                ON CONFLICT (subject_kind, subject_id) WHERE status = 'QUEUED'
                DO UPDATE SET
                    owner_id = EXCLUDED.owner_id,
                    binding_id = EXCLUDED.binding_id,
                    target_dataset_kind = EXCLUDED.target_dataset_kind,
                    target_dataset_version_id = EXCLUDED.target_dataset_version_id,
                    mapping_snapshot = EXCLUDED.mapping_snapshot,
                    mapping_revision = EXCLUDED.mapping_revision,
                    trigger_type = EXCLUDED.trigger_type,
                    requested_at = now(),
                    parent_run_id = EXCLUDED.parent_run_id
                RETURNING id
                """, UUID.class,
                candidate.getId(),
                candidate.getOwnerId(),
                candidate.getSubjectKind().name(),
                candidate.getSubjectId(),
                candidate.getBindingId(),
                candidate.getTargetDatasetKind().name(),
                candidate.getTargetDatasetVersionId(),
                mapping,
                candidate.getMappingRevision(),
                candidate.getTriggerType().name(),
                candidate.getAttempt(),
                candidate.getParentRunId()
        );
        if (id == null) {
            throw new IllegalStateException("queued run upsert returned no id");
        }
        OntologyIndexRunEntity entity = entityManager.find(OntologyIndexRunEntity.class, id);
        if (entity == null) {
            throw new IllegalStateException("queued run upsert returned an unknown id");
        }
        entityManager.refresh(entity);
        return entity;
    }
}
