package com.ontoweave.data.ontology.binding.api;

import com.ontoweave.data.ontology.binding.api.dto.GenerateRequest;
import com.ontoweave.data.ontology.binding.api.dto.GenerateResponse;
import com.ontoweave.data.ontology.binding.api.dto.IndexRunResponse;
import com.ontoweave.data.ontology.binding.api.dto.MappingSaveRequest;
import com.ontoweave.data.ontology.binding.api.dto.MappingSaveResponse;
import com.ontoweave.data.ontology.binding.api.dto.PreviewBindingRequest;
import com.ontoweave.data.ontology.binding.api.dto.RelationBindingResponse;
import com.ontoweave.data.ontology.binding.api.dto.RelationDefinitionRequest;
import com.ontoweave.data.ontology.binding.api.dto.RelationDefinitionResponse;
import com.ontoweave.data.ontology.binding.api.dto.RelationOverviewResponse;
import com.ontoweave.data.ontology.binding.api.dto.RelationPreviewResponse;
import com.ontoweave.data.ontology.binding.preview.MappingPreviewService;
import com.ontoweave.data.ontology.binding.service.BindingRunService;
import com.ontoweave.data.ontology.binding.service.RelationBindingService;
import com.ontoweave.data.ontology.binding.service.RelationDefinitionService;
import com.ontoweave.data.ontology.binding.service.RelationOverviewService;
import com.ontoweave.data.ontology.binding.support.BindingErrors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class OntologyRelationController {

    private final RelationDefinitionService definitions;
    private final RelationBindingService bindings;
    private final BindingRunService runs;
    private final RelationOverviewService overviews;
    private final MappingPreviewService previews;

    public OntologyRelationController(
            RelationDefinitionService definitions,
            RelationBindingService bindings,
            BindingRunService runs,
            RelationOverviewService overviews,
            MappingPreviewService previews
    ) {
        this.definitions = definitions;
        this.bindings = bindings;
        this.runs = runs;
        this.overviews = overviews;
        this.previews = previews;
    }

    @PutMapping("/internal/v1/ontology/relation-definitions/{relationId}")
    public RelationDefinitionResponse upsertDefinition(
            @PathVariable UUID relationId,
            @RequestParam(required = false) UUID ownerId,
            @RequestBody RelationDefinitionRequest request
    ) {
        if (request == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "请求体不能为空");
        }
        UUID resolved = BindingOwnerResolver.resolve(ownerId, request.ownerId());
        return definitions.upsert(relationId, new RelationDefinitionRequest(
                resolved,
                request.sourceTypeId(),
                request.targetTypeId(),
                request.name(),
                request.cardinality(),
                request.foreignKeyPropertyId(),
                request.foreignKeyDatatype(),
                request.sourcePrimaryKeyAttributeId(),
                request.sourcePrimaryKeyDatatype(),
                request.targetPrimaryKeyAttributeId(),
                request.targetPrimaryKeyDatatype(),
                request.definitionRevision()
        ));
    }

    @GetMapping("/internal/v1/ontology/relation-types/{relationId}/binding")
    public RelationBindingResponse get(
            @PathVariable UUID relationId,
            @RequestParam UUID ownerId
    ) {
        return bindings.get(relationId, ownerId);
    }

    @PutMapping("/internal/v1/ontology/relation-types/{relationId}/binding")
    public MappingSaveResponse save(
            @PathVariable UUID relationId,
            @RequestParam(required = false) UUID ownerId,
            @RequestBody MappingSaveRequest request
    ) {
        if (request == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "请求体不能为空");
        }
        UUID resolved = BindingOwnerResolver.resolve(ownerId, request.ownerId());
        return bindings.save(relationId, new MappingSaveRequest(
                resolved,
                request.baseRevision(),
                request.datasetKind(),
                request.datasetId(),
                request.mapping()
        ));
    }

    @DeleteMapping("/internal/v1/ontology/relation-types/{relationId}/binding")
    public ResponseEntity<Void> delete(
            @PathVariable UUID relationId,
            @RequestParam UUID ownerId
    ) {
        bindings.unbind(relationId, ownerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/v1/ontology/relation-types/{relationId}/binding/preview")
    public RelationPreviewResponse preview(
            @PathVariable UUID relationId,
            @RequestParam(required = false) UUID ownerId,
            @RequestBody(required = false) PreviewBindingRequest request
    ) {
        UUID resolved = BindingOwnerResolver.resolve(
                ownerId, request == null ? null : request.ownerId()
        );
        return previews.previewRelation(relationId, resolved, request == null
                ? null
                : new PreviewBindingRequest(
                        resolved,
                        request.datasetKind(),
                        request.datasetId(),
                        request.mapping(),
                        request.limit()
                ));
    }

    @PostMapping("/internal/v1/ontology/relation-types/{relationId}/binding/generate")
    public GenerateResponse generate(
            @PathVariable UUID relationId,
            @RequestParam(required = false) UUID ownerId,
            @RequestBody(required = false) GenerateRequest request
    ) {
        UUID resolved = BindingOwnerResolver.resolve(
                ownerId, request == null ? null : request.ownerId()
        );
        if (request == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "mappingRevision 不能为空");
        }
        return bindings.generate(relationId, new GenerateRequest(
                resolved, request.mappingRevision()
        ));
    }

    @GetMapping("/internal/v1/ontology/relation-types/{relationId}/binding/runs")
    public List<IndexRunResponse> runs(
            @PathVariable UUID relationId,
            @RequestParam UUID ownerId
    ) {
        return runs.relationRuns(relationId, ownerId);
    }

    @GetMapping("/internal/v1/ontology/relation-types/{relationId}/overview")
    public RelationOverviewResponse overview(
            @PathVariable UUID relationId,
            @RequestParam UUID ownerId
    ) {
        return overviews.overview(relationId, ownerId);
    }
}
