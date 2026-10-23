package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.ValidationState;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;

import java.util.UUID;

public record RelationBindingResponse(
        UUID bindingId,
        UUID ownerId,
        UUID relationId,
        String status,
        long revision,
        DatasetKind draftDatasetKind,
        UUID draftDatasetId,
        MappingSnapshot draftMapping,
        DatasetKind activeDatasetKind,
        UUID activeDatasetId,
        UUID activeDatasetVersionId,
        Long activeMappingRevision,
        UUID currentIndexVersionId,
        ValidationState validationState,
        MappingValidationResult validation,
        IndexRunResponse recentRun
) {
}
