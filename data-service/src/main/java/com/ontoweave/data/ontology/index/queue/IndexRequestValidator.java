package com.ontoweave.data.ontology.index.queue;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.mapping.MappingIssueCode;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.mapping.MappingValidator;
import com.ontoweave.data.ontology.mapping.OntologyAttributeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class IndexRequestValidator {

    private final MappingValidator validator = new MappingValidator();

    public MappingValidationResult validateObject(MappingSnapshot mapping, DatasetRef dataset) {
        return validator.validate(mapping, dataset, definitions(mapping), "confirmed");
    }

    public MappingValidationResult validateRelation(
            MappingSnapshot mapping,
            DatasetRef dataset,
            OntologyRelationDefinitionEntity definition
    ) {
        if (definition == null) {
            return new MappingValidationResult(
                    List.of(new MappingValidationResult.MappingIssue(
                            MappingIssueCode.ATTRIBUTE_NOT_FOUND,
                            "关系定义不存在",
                            null,
                            null
                    )),
                    List.of()
            );
        }
        if (!"many_to_many".equals(definition.getCardinality())) {
            return new MappingValidationResult(
                    List.of(new MappingValidationResult.MappingIssue(
                            MappingIssueCode.MAPPING_KIND_INVALID,
                            "关系绑定只支持多对多关系",
                            null,
                            null
                    )),
                    List.of()
            );
        }
        return validator.validateRelation(
                mapping,
                dataset,
                "confirmed",
                definition.getSourcePrimaryKeyDatatype(),
                definition.getTargetPrimaryKeyDatatype()
        );
    }

    private List<OntologyAttributeDefinition> definitions(MappingSnapshot mapping) {
        if (mapping == null) {
            return List.of();
        }
        return mapping.attributes().stream()
                .map(attribute -> new OntologyAttributeDefinition(
                        attribute.attributeId(),
                        null,
                        attribute.datatype(),
                        attribute.required(),
                        attribute.isArray(),
                        attribute.enumValues(),
                        null
                ))
                .filter(attribute -> attribute.attributeId() != null)
                .toList();
    }
}
