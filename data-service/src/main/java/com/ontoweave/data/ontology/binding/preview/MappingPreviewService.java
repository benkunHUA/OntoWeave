package com.ontoweave.data.ontology.binding.preview;

import com.ontoweave.data.ontology.binding.api.dto.ObjectPreviewResponse;
import com.ontoweave.data.ontology.binding.api.dto.PreviewBindingRequest;
import com.ontoweave.data.ontology.binding.api.dto.RelationPreviewResponse;
import com.ontoweave.data.ontology.binding.service.ObjectBindingService;
import com.ontoweave.data.ontology.binding.service.RelationBindingService;
import com.ontoweave.data.ontology.binding.service.RelationDefinitionService;
import com.ontoweave.data.ontology.binding.support.BindingJson;
import com.ontoweave.data.ontology.binding.validation.BindingCheckService;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationBindingEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MappingPreviewService {

    private final ObjectBindingService objectBindings;
    private final RelationBindingService relationBindings;
    private final RelationDefinitionService definitions;
    private final BindingCheckService checks;
    private final BindingJson json;
    private final ObjectPreviewRenderer objectRenderer;
    private final RelationPreviewRenderer relationRenderer;

    public MappingPreviewService(
            ObjectBindingService objectBindings,
            RelationBindingService relationBindings,
            RelationDefinitionService definitions,
            BindingCheckService checks,
            BindingJson json,
            ObjectPreviewRenderer objectRenderer,
            RelationPreviewRenderer relationRenderer
    ) {
        this.objectBindings = objectBindings;
        this.relationBindings = relationBindings;
        this.definitions = definitions;
        this.checks = checks;
        this.json = json;
        this.objectRenderer = objectRenderer;
        this.relationRenderer = relationRenderer;
    }

    @Transactional(readOnly = true)
    public ObjectPreviewResponse previewObject(
            UUID objectTypeId,
            UUID ownerId,
            PreviewBindingRequest request
    ) {
        OntologyObjectBindingEntity binding = objectBindings.require(objectTypeId, ownerId);
        MappingSnapshot mapping = request != null && request.mapping() != null
                ? ObjectBindingService.withRevision(request.mapping(), binding.getRevision())
                : json.mapping(binding.getDraftMapping());
        var kind = request != null && request.datasetKind() != null
                ? request.datasetKind()
                : binding.getDraftDatasetKind();
        UUID datasetId = request != null && request.datasetId() != null
                ? request.datasetId()
                : binding.getDraftDatasetId();
        BindingCheckService.CheckResult check = checks.checkObject(kind, datasetId, ownerId, mapping);
        if (!check.validation().isValid()) {
            return new ObjectPreviewResponse(
                    List.of(),
                    check.validation().blocking(),
                    check.validation().warnings(),
                    List.of()
            );
        }
        return objectRenderer.render(check.dataset(), mapping, check.validation(), PreviewSupport.limit(request));
    }

    @Transactional(readOnly = true)
    public RelationPreviewResponse previewRelation(
            UUID relationId,
            UUID ownerId,
            PreviewBindingRequest request
    ) {
        OntologyRelationBindingEntity binding = relationBindings.require(relationId, ownerId);
        var definition = definitions.require(relationId, ownerId);
        MappingSnapshot mapping = request != null && request.mapping() != null
                ? ObjectBindingService.withRevision(request.mapping(), binding.getRevision())
                : json.mapping(binding.getDraftMapping());
        var kind = request != null && request.datasetKind() != null
                ? request.datasetKind()
                : binding.getDraftDatasetKind();
        UUID datasetId = request != null && request.datasetId() != null
                ? request.datasetId()
                : binding.getDraftDatasetId();
        BindingCheckService.CheckResult check = checks.checkRelation(
                kind, datasetId, ownerId, mapping, definition
        );
        if (!check.validation().isValid()) {
            return new RelationPreviewResponse(
                    List.of(),
                    check.validation().blocking(),
                    check.validation().warnings(),
                    List.of()
            );
        }
        return relationRenderer.render(
                check, mapping, definition, ownerId, check.validation(), PreviewSupport.limit(request)
        );
    }
}
