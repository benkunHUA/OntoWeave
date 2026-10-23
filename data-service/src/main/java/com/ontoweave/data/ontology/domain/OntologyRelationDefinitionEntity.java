package com.ontoweave.data.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ontology_relation_definition", schema = "data")
public class OntologyRelationDefinitionEntity {

    @Id
    @Column(name = "relation_id")
    private UUID relationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "source_type_id", nullable = false)
    private UUID sourceTypeId;

    @Column(name = "target_type_id", nullable = false)
    private UUID targetTypeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String cardinality;

    @Column(name = "foreign_key_property_id")
    private UUID foreignKeyPropertyId;

    @Column(name = "foreign_key_datatype")
    private String foreignKeyDatatype;

    @Column(name = "source_primary_key_attribute_id", nullable = false)
    private UUID sourcePrimaryKeyAttributeId;

    @Column(name = "source_primary_key_datatype", nullable = false)
    private String sourcePrimaryKeyDatatype;

    @Column(name = "target_primary_key_attribute_id", nullable = false)
    private UUID targetPrimaryKeyAttributeId;

    @Column(name = "target_primary_key_datatype", nullable = false)
    private String targetPrimaryKeyDatatype;

    @Column(name = "definition_revision", nullable = false)
    private long definitionRevision;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected OntologyRelationDefinitionEntity() {
    }

    public OntologyRelationDefinitionEntity(
            UUID relationId,
            UUID ownerId,
            UUID sourceTypeId,
            UUID targetTypeId,
            String name,
            String cardinality,
            UUID foreignKeyPropertyId,
            String foreignKeyDatatype,
            UUID sourcePrimaryKeyAttributeId,
            String sourcePrimaryKeyDatatype,
            UUID targetPrimaryKeyAttributeId,
            String targetPrimaryKeyDatatype,
            long definitionRevision
    ) {
        this.relationId = relationId;
        this.ownerId = ownerId;
        this.sourceTypeId = sourceTypeId;
        this.targetTypeId = targetTypeId;
        this.name = name;
        this.cardinality = cardinality;
        this.foreignKeyPropertyId = foreignKeyPropertyId;
        this.foreignKeyDatatype = foreignKeyDatatype;
        this.sourcePrimaryKeyAttributeId = sourcePrimaryKeyAttributeId;
        this.sourcePrimaryKeyDatatype = sourcePrimaryKeyDatatype;
        this.targetPrimaryKeyAttributeId = targetPrimaryKeyAttributeId;
        this.targetPrimaryKeyDatatype = targetPrimaryKeyDatatype;
        this.definitionRevision = definitionRevision;
    }

    @PrePersist
    void onCreate() {
        if (syncedAt == null) {
            syncedAt = Instant.now();
        }
    }

    public UUID getRelationId() {
        return relationId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getSourceTypeId() {
        return sourceTypeId;
    }

    public UUID getTargetTypeId() {
        return targetTypeId;
    }

    public String getName() {
        return name;
    }

    public String getCardinality() {
        return cardinality;
    }

    public UUID getForeignKeyPropertyId() {
        return foreignKeyPropertyId;
    }

    public String getForeignKeyDatatype() {
        return foreignKeyDatatype;
    }

    public UUID getSourcePrimaryKeyAttributeId() {
        return sourcePrimaryKeyAttributeId;
    }

    public String getSourcePrimaryKeyDatatype() {
        return sourcePrimaryKeyDatatype;
    }

    public UUID getTargetPrimaryKeyAttributeId() {
        return targetPrimaryKeyAttributeId;
    }

    public String getTargetPrimaryKeyDatatype() {
        return targetPrimaryKeyDatatype;
    }

    public long getDefinitionRevision() {
        return definitionRevision;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
