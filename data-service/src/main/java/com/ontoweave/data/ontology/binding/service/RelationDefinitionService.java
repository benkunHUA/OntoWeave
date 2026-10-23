package com.ontoweave.data.ontology.binding.service;

import com.ontoweave.data.ontology.binding.api.dto.RelationDefinitionRequest;
import com.ontoweave.data.ontology.binding.api.dto.RelationDefinitionResponse;
import com.ontoweave.data.ontology.binding.support.BindingErrors;
import com.ontoweave.data.ontology.binding.support.BindingTypeRules;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.repository.OntologyRelationDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class RelationDefinitionService {

    static final String MANY_TO_MANY = "many_to_many";
    private static final Set<String> CARDINALITIES = Set.of(
            "one_to_one", "one_to_many", MANY_TO_MANY
    );

    private final OntologyRelationDefinitionRepository definitions;

    public RelationDefinitionService(OntologyRelationDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @Transactional
    public RelationDefinitionResponse upsert(UUID relationId, RelationDefinitionRequest request) {
        ValidatedDefinition validated = validate(relationId, request);
        OntologyRelationDefinitionEntity existing = definitions.findById(relationId).orElse(null);
        if (existing != null && !existing.getOwnerId().equals(request.ownerId())) {
            throw BindingErrors.ownerMismatch();
        }
        definitions.upsert(
                relationId,
                request.ownerId(),
                request.sourceTypeId(),
                request.targetTypeId(),
                request.name().trim(),
                validated.cardinality(),
                request.foreignKeyPropertyId(),
                blankToNull(request.foreignKeyDatatype()),
                request.sourcePrimaryKeyAttributeId(),
                request.sourcePrimaryKeyDatatype(),
                request.targetPrimaryKeyAttributeId(),
                request.targetPrimaryKeyDatatype(),
                request.definitionRevision()
        );
        return response(require(relationId, request.ownerId()));
    }

    @Transactional(readOnly = true)
    public OntologyRelationDefinitionEntity require(UUID relationId, UUID ownerId) {
        return definitions.findByRelationId(relationId)
                .filter(definition -> definition.getOwnerId().equals(ownerId))
                .orElseThrow(BindingErrors::bindingNotFound);
    }

    @Transactional(readOnly = true)
    public RelationDefinitionResponse get(UUID relationId, UUID ownerId) {
        return response(require(relationId, ownerId));
    }

    public RelationDefinitionResponse response(OntologyRelationDefinitionEntity definition) {
        return new RelationDefinitionResponse(
                definition.getRelationId(),
                definition.getOwnerId(),
                definition.getSourceTypeId(),
                definition.getTargetTypeId(),
                definition.getName(),
                definition.getCardinality(),
                definition.getForeignKeyPropertyId(),
                definition.getForeignKeyDatatype(),
                definition.getSourcePrimaryKeyAttributeId(),
                definition.getSourcePrimaryKeyDatatype(),
                definition.getTargetPrimaryKeyAttributeId(),
                definition.getTargetPrimaryKeyDatatype(),
                definition.getDefinitionRevision(),
                definition.getSyncedAt(),
                warnings(definition)
        );
    }

    public List<MappingValidationResult.MappingIssue> warnings(
            OntologyRelationDefinitionEntity definition
    ) {
        if (MANY_TO_MANY.equals(definition.getCardinality())) {
            return List.of();
        }
        List<MappingValidationResult.MappingIssue> warnings = new ArrayList<>();
        if (!BindingTypeRules.foreignKeyCompatible(
                definition.getForeignKeyDatatype(),
                definition.getTargetPrimaryKeyDatatype()
        )) {
            warnings.add(new MappingValidationResult.MappingIssue(
                    "FOREIGN_KEY_TYPE_INCOMPATIBLE",
                    "外键属性类型与目标主键不一致，对应关系不会生成",
                    definition.getForeignKeyPropertyId(),
                    null
            ));
        }
        return List.copyOf(warnings);
    }

    private ValidatedDefinition validate(UUID relationId, RelationDefinitionRequest request) {
        if (relationId == null || request == null || request.ownerId() == null) {
            throw BindingErrors.invalidRelationDefinition("关系 id、ownerId 不能为空");
        }
        if (request.sourceTypeId() == null || request.targetTypeId() == null) {
            throw BindingErrors.invalidRelationDefinition("关系两端类型不能为空");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw BindingErrors.invalidRelationDefinition("关系名称不能为空");
        }
        String cardinality = request.cardinality() == null
                ? ""
                : request.cardinality().trim().toLowerCase(Locale.ROOT);
        if (!CARDINALITIES.contains(cardinality)) {
            throw BindingErrors.invalidRelationDefinition("关系基数不合法");
        }
        if (request.definitionRevision() < 1) {
            throw BindingErrors.invalidRelationDefinition("definitionRevision 必须大于 0");
        }
        if (request.sourcePrimaryKeyAttributeId() == null
                || blank(request.sourcePrimaryKeyDatatype())) {
            throw BindingErrors.invalidRelationDefinition("源端主键属性不完整");
        }
        if (request.targetPrimaryKeyAttributeId() == null
                || blank(request.targetPrimaryKeyDatatype())) {
            throw BindingErrors.invalidRelationDefinition("目标端主键属性不完整");
        }
        if (MANY_TO_MANY.equals(cardinality)) {
            if (request.foreignKeyPropertyId() != null || !blank(request.foreignKeyDatatype())) {
                throw BindingErrors.invalidRelationDefinition("多对多关系不能携带外键属性");
            }
        } else if (request.foreignKeyPropertyId() == null
                || blank(request.foreignKeyDatatype())) {
            throw BindingErrors.invalidRelationDefinition("外键关系必须提供外键属性");
        }
        return new ValidatedDefinition(cardinality);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return blank(value) ? null : value;
    }

    private record ValidatedDefinition(String cardinality) {
    }
}
