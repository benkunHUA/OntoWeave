package com.ontoweave.data.ontology.repository;

import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OntologyRelationDefinitionRepository
        extends JpaRepository<OntologyRelationDefinitionEntity, UUID> {

    Optional<OntologyRelationDefinitionEntity> findByRelationId(UUID relationId);

    List<OntologyRelationDefinitionEntity> findByOwnerIdAndSourceTypeId(
            UUID ownerId,
            UUID sourceTypeId
    );

    List<OntologyRelationDefinitionEntity> findByOwnerIdAndTargetTypeId(
            UUID ownerId,
            UUID targetTypeId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            INSERT INTO data.ontology_relation_definition
                (relation_id, owner_id, source_type_id, target_type_id, name, cardinality,
                 foreign_key_property_id, foreign_key_datatype,
                 source_primary_key_attribute_id, source_primary_key_datatype,
                 target_primary_key_attribute_id, target_primary_key_datatype,
                 definition_revision, synced_at)
            VALUES
                (:relationId, :ownerId, :sourceTypeId, :targetTypeId, :name, :cardinality,
                 :foreignKeyPropertyId, :foreignKeyDatatype,
                 :sourcePrimaryKeyAttributeId, :sourcePrimaryKeyDatatype,
                 :targetPrimaryKeyAttributeId, :targetPrimaryKeyDatatype,
                 :definitionRevision, now())
            ON CONFLICT (relation_id) DO UPDATE SET
                owner_id = EXCLUDED.owner_id,
                source_type_id = EXCLUDED.source_type_id,
                target_type_id = EXCLUDED.target_type_id,
                name = EXCLUDED.name,
                cardinality = EXCLUDED.cardinality,
                foreign_key_property_id = EXCLUDED.foreign_key_property_id,
                foreign_key_datatype = EXCLUDED.foreign_key_datatype,
                source_primary_key_attribute_id = EXCLUDED.source_primary_key_attribute_id,
                source_primary_key_datatype = EXCLUDED.source_primary_key_datatype,
                target_primary_key_attribute_id = EXCLUDED.target_primary_key_attribute_id,
                target_primary_key_datatype = EXCLUDED.target_primary_key_datatype,
                definition_revision = EXCLUDED.definition_revision,
                synced_at = now()
            """, nativeQuery = true)
    int upsert(
            @Param("relationId") UUID relationId,
            @Param("ownerId") UUID ownerId,
            @Param("sourceTypeId") UUID sourceTypeId,
            @Param("targetTypeId") UUID targetTypeId,
            @Param("name") String name,
            @Param("cardinality") String cardinality,
            @Param("foreignKeyPropertyId") UUID foreignKeyPropertyId,
            @Param("foreignKeyDatatype") String foreignKeyDatatype,
            @Param("sourcePrimaryKeyAttributeId") UUID sourcePrimaryKeyAttributeId,
            @Param("sourcePrimaryKeyDatatype") String sourcePrimaryKeyDatatype,
            @Param("targetPrimaryKeyAttributeId") UUID targetPrimaryKeyAttributeId,
            @Param("targetPrimaryKeyDatatype") String targetPrimaryKeyDatatype,
            @Param("definitionRevision") long definitionRevision
    );
}
