package com.ontoweave.data.ontology.binding.service;

import com.ontoweave.data.ontology.binding.api.dto.ObjectBindingSummary;
import com.ontoweave.data.ontology.binding.api.dto.RelationOverviewResponse;
import com.ontoweave.data.ontology.binding.api.dto.RelationVersionSummary;
import com.ontoweave.data.ontology.binding.support.BindingJson;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.repository.OntologyObjectBindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RelationOverviewService {

    private final RelationDefinitionService definitions;
    private final OntologyObjectBindingRepository bindings;
    private final BindingRunService runs;
    private final BindingJson json;

    public RelationOverviewService(
            RelationDefinitionService definitions,
            OntologyObjectBindingRepository bindings,
            BindingRunService runs,
            BindingJson json
    ) {
        this.definitions = definitions;
        this.bindings = bindings;
        this.runs = runs;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public RelationOverviewResponse overview(UUID relationId, UUID ownerId) {
        OntologyRelationDefinitionEntity definition = definitions.require(relationId, ownerId);
        List<MappingValidationResult.MappingIssue> warnings = new ArrayList<>(
                definitions.warnings(definition)
        );
        OntologyObjectBindingEntity source = binding(
                definition.getSourceTypeId(), ownerId
        ).orElse(null);
        addForeignKeyWarnings(definition, source, warnings);
        RelationVersionSummary latestVersion = runs.latestRelationVersion(relationId, ownerId);
        return new RelationOverviewResponse(
                definitions.response(definition),
                summary(definition.getSourceTypeId(), source),
                summary(definition.getTargetTypeId(), binding(
                        definition.getTargetTypeId(), ownerId
                ).orElse(null)),
                warnings,
                latestVersion,
                runs.recentRelationRun(relationId, ownerId)
        );
    }

    private void addForeignKeyWarnings(
            OntologyRelationDefinitionEntity definition,
            OntologyObjectBindingEntity source,
            List<MappingValidationResult.MappingIssue> warnings
    ) {
        if (RelationDefinitionService.MANY_TO_MANY.equals(definition.getCardinality())
                || source == null) {
            return;
        }
        MappingSnapshot mapping = json.mapping(source.getDraftMapping());
        if (mapping == null || definition.getForeignKeyPropertyId() == null) {
            return;
        }
        boolean mapped = mapping.attributes().stream()
                .anyMatch(attribute -> definition.getForeignKeyPropertyId()
                        .equals(attribute.attributeId()));
        if (!mapped) {
            warnings.add(new MappingValidationResult.MappingIssue(
                    "FOREIGN_KEY_UNMAPPED",
                    "外键属性未映射，对应关系不会生成",
                    definition.getForeignKeyPropertyId(),
                    null
            ));
        }
    }

    private java.util.Optional<OntologyObjectBindingEntity> binding(
            UUID objectTypeId,
            UUID ownerId
    ) {
        return bindings.findByObjectTypeIdAndOwnerId(objectTypeId, ownerId)
                .filter(binding -> binding.getStatus() == OntologyObjectBindingEntity.BindingStatus.ACTIVE);
    }

    private ObjectBindingSummary summary(
            UUID objectTypeId,
            OntologyObjectBindingEntity binding
    ) {
        if (binding == null) {
            return new ObjectBindingSummary(objectTypeId, false, null, null, null);
        }
        return new ObjectBindingSummary(
                objectTypeId,
                true,
                binding.getStatus().name(),
                binding.getCurrentIndexVersionId(),
                binding.getActiveDatasetId()
        );
    }
}
