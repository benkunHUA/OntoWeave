package com.ontoweave.data.ontology.repository;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.ValidationState;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OntologyObjectBindingRepository
        extends JpaRepository<OntologyObjectBindingEntity, UUID> {

    Optional<OntologyObjectBindingEntity> findByObjectTypeId(UUID objectTypeId);

    Optional<OntologyObjectBindingEntity> findByObjectTypeIdAndOwnerId(
            UUID objectTypeId,
            UUID ownerId
    );

    List<OntologyObjectBindingEntity> findByDraftDatasetKindAndDraftDatasetId(
            DatasetKind kind,
            UUID datasetId
    );

    List<OntologyObjectBindingEntity>
            findByActiveDatasetKindAndActiveDatasetIdAndValidationState(
                    DatasetKind kind,
                    UUID datasetId,
                    ValidationState validationState
            );

    boolean existsByDraftDatasetKindAndDraftDatasetIdAndObjectTypeIdNot(
            DatasetKind kind,
            UUID datasetId,
            UUID objectTypeId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_object_binding
            SET draft_dataset_kind = :#{#draftDatasetKind.name()},
                draft_dataset_id = :draftDatasetId,
                draft_mapping = CAST(:mapping AS jsonb),
                validation_state = :#{#validationState.name()},
                validation_error = CAST(:validationError AS jsonb),
                revision = revision + 1,
                updated_at = now()
            WHERE id = :id AND revision = :baseRevision AND status = 'ACTIVE'
            """, nativeQuery = true)
    int updateDraft(
            @Param("id") UUID id,
            @Param("baseRevision") long baseRevision,
            @Param("draftDatasetKind") DatasetKind draftDatasetKind,
            @Param("draftDatasetId") UUID draftDatasetId,
            @Param("mapping") String mapping,
            @Param("validationState") ValidationState validationState,
            @Param("validationError") String validationError
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_object_binding
            SET active_dataset_kind = :#{#activeDatasetKind.name()},
                active_dataset_id = :activeDatasetId,
                active_dataset_version_id = :activeDatasetVersionId,
                active_mapping_revision = :activeMappingRevision,
                current_index_version_id = :currentIndexVersionId,
                validation_state = 'OK',
                validation_error = NULL,
                updated_at = now()
            WHERE id = :id
            """, nativeQuery = true)
    int activateVersion(
            @Param("id") UUID id,
            @Param("activeDatasetKind") DatasetKind activeDatasetKind,
            @Param("activeDatasetId") UUID activeDatasetId,
            @Param("activeDatasetVersionId") UUID activeDatasetVersionId,
            @Param("activeMappingRevision") long activeMappingRevision,
            @Param("currentIndexVersionId") UUID currentIndexVersionId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_object_binding
            SET status = 'UNBOUND',
                active_dataset_kind = NULL,
                active_dataset_id = NULL,
                active_dataset_version_id = NULL,
                active_mapping_revision = NULL,
                current_index_version_id = NULL,
                updated_at = now()
            WHERE id = :id
            """, nativeQuery = true)
    int unbind(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE data.ontology_object_binding
            SET validation_state = 'NEEDS_REPAIR',
                validation_error = CAST(:validationError AS jsonb),
                updated_at = now()
            WHERE id = :id
            """, nativeQuery = true)
    int markNeedsRepair(
            @Param("id") UUID id,
            @Param("validationError") String validationError
    );
}
