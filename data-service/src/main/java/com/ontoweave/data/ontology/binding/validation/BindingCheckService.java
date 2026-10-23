package com.ontoweave.data.ontology.binding.validation;

import com.ontoweave.data.ontology.binding.support.BindingErrors;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.dataset.DatasetUnavailableException;
import com.ontoweave.data.ontology.dataset.OntologyDatasetCatalog;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.mapping.MappingValidator;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BindingCheckService {

    private final OntologyDatasetCatalog catalog;
    private final MappingValidator validator = new MappingValidator();

    public BindingCheckService(OntologyDatasetCatalog catalog) {
        this.catalog = catalog;
    }

    public CheckResult checkObject(
            DatasetKind kind,
            UUID datasetId,
            UUID ownerId,
            MappingSnapshot mapping
    ) {
        DatasetRef dataset = resolve(kind, datasetId, ownerId);
        MappingValidationResult validation = validator.validate(
                mapping,
                dataset,
                BindingMappingDefinitions.from(mapping),
                "confirmed"
        );
        return new CheckResult(dataset, validation);
    }

    public CheckResult checkRelation(
            DatasetKind kind,
            UUID datasetId,
            UUID ownerId,
            MappingSnapshot mapping,
            OntologyRelationDefinitionEntity definition
    ) {
        DatasetRef dataset = resolve(kind, datasetId, ownerId);
        MappingValidationResult validation = validator.validateRelation(
                mapping,
                dataset,
                "confirmed",
                definition.getSourcePrimaryKeyDatatype(),
                definition.getTargetPrimaryKeyDatatype()
        );
        return new CheckResult(dataset, validation);
    }

    public DatasetRef resolve(DatasetKind kind, UUID datasetId, UUID ownerId) {
        try {
            return catalog.requireCurrent(kind, datasetId, ownerId);
        } catch (DatasetUnavailableException exception) {
            throw BindingErrors.datasetNotFound(kind, datasetId);
        }
    }

    public record CheckResult(
            DatasetRef dataset,
            MappingValidationResult validation
    ) {
    }
}
