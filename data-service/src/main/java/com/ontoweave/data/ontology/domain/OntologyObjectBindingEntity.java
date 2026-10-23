package com.ontoweave.data.ontology.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.ValidationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ontology_object_binding", schema = "data")
public class OntologyObjectBindingEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "object_type_id", nullable = false)
    private UUID objectTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BindingStatus status;

    @Column(nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_dataset_kind", nullable = false)
    private DatasetKind draftDatasetKind;

    @Column(name = "draft_dataset_id", nullable = false)
    private UUID draftDatasetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_mapping", nullable = false)
    private JsonNode draftMapping;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_dataset_kind")
    private DatasetKind activeDatasetKind;

    @Column(name = "active_dataset_id")
    private UUID activeDatasetId;

    @Column(name = "active_dataset_version_id")
    private UUID activeDatasetVersionId;

    @Column(name = "active_mapping_revision")
    private Long activeMappingRevision;

    @Column(name = "current_index_version_id")
    private UUID currentIndexVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_state", nullable = false)
    private ValidationState validationState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_error")
    private JsonNode validationError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OntologyObjectBindingEntity() {
    }

    public OntologyObjectBindingEntity(
            UUID id,
            UUID ownerId,
            UUID objectTypeId,
            DatasetKind draftDatasetKind,
            UUID draftDatasetId,
            JsonNode draftMapping
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.objectTypeId = objectTypeId;
        this.status = BindingStatus.ACTIVE;
        this.revision = 1L;
        this.draftDatasetKind = draftDatasetKind;
        this.draftDatasetId = draftDatasetId;
        this.draftMapping = draftMapping;
        this.validationState = ValidationState.OK;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = BindingStatus.ACTIVE;
        }
        if (validationState == null) {
            validationState = ValidationState.OK;
        }
        if (revision == 0L) {
            revision = 1L;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getObjectTypeId() {
        return objectTypeId;
    }

    public BindingStatus getStatus() {
        return status;
    }

    public long getRevision() {
        return revision;
    }

    public DatasetKind getDraftDatasetKind() {
        return draftDatasetKind;
    }

    public UUID getDraftDatasetId() {
        return draftDatasetId;
    }

    public JsonNode getDraftMapping() {
        return draftMapping;
    }

    public DatasetKind getActiveDatasetKind() {
        return activeDatasetKind;
    }

    public UUID getActiveDatasetId() {
        return activeDatasetId;
    }

    public UUID getActiveDatasetVersionId() {
        return activeDatasetVersionId;
    }

    public Long getActiveMappingRevision() {
        return activeMappingRevision;
    }

    public UUID getCurrentIndexVersionId() {
        return currentIndexVersionId;
    }

    public ValidationState getValidationState() {
        return validationState;
    }

    public JsonNode getValidationError() {
        return validationError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum BindingStatus {
        ACTIVE,
        UNBOUND
    }
}
