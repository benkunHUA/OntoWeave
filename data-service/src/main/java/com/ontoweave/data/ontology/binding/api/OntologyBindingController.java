package com.ontoweave.data.ontology.binding.api;

import com.ontoweave.data.ontology.binding.api.dto.GenerateRequest;
import com.ontoweave.data.ontology.binding.api.dto.GenerateResponse;
import com.ontoweave.data.ontology.binding.api.dto.IndexRunResponse;
import com.ontoweave.data.ontology.binding.api.dto.MappingSaveRequest;
import com.ontoweave.data.ontology.binding.api.dto.MappingSaveResponse;
import com.ontoweave.data.ontology.binding.api.dto.ObjectBindingResponse;
import com.ontoweave.data.ontology.binding.api.dto.ObjectPreviewResponse;
import com.ontoweave.data.ontology.binding.api.dto.PreviewBindingRequest;
import com.ontoweave.data.ontology.binding.api.dto.ValidateBindingRequest;
import com.ontoweave.data.ontology.binding.preview.MappingPreviewService;
import com.ontoweave.data.ontology.binding.service.BindingRunService;
import com.ontoweave.data.ontology.binding.service.ObjectBindingService;
import com.ontoweave.data.ontology.binding.support.BindingErrors;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
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
public class OntologyBindingController {

    private final ObjectBindingService bindings;
    private final BindingRunService runs;
    private final MappingPreviewService previews;

    public OntologyBindingController(
            ObjectBindingService bindings,
            BindingRunService runs,
            MappingPreviewService previews
    ) {
        this.bindings = bindings;
        this.runs = runs;
        this.previews = previews;
    }

    @GetMapping("/internal/v1/ontology/object-types/{objectTypeId}/binding")
    public ObjectBindingResponse get(
            @PathVariable UUID objectTypeId,
            @RequestParam UUID ownerId
    ) {
        return bindings.get(objectTypeId, ownerId);
    }

    @PutMapping("/internal/v1/ontology/object-types/{objectTypeId}/binding")
    public MappingSaveResponse save(
            @PathVariable UUID objectTypeId,
            @RequestParam(required = false) UUID ownerId,
            @RequestBody MappingSaveRequest request
    ) {
        if (request == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "请求体不能为空");
        }
        UUID resolved = BindingOwnerResolver.resolve(ownerId, request.ownerId());
        return bindings.save(objectTypeId, new MappingSaveRequest(
                resolved,
                request.baseRevision(),
                request.datasetKind(),
                request.datasetId(),
                request.mapping()
        ));
    }

    @DeleteMapping("/internal/v1/ontology/object-types/{objectTypeId}/binding")
    public ResponseEntity<Void> delete(
            @PathVariable UUID objectTypeId,
            @RequestParam UUID ownerId
    ) {
        bindings.unbind(objectTypeId, ownerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/v1/ontology/object-types/{objectTypeId}/binding/validate")
    public MappingValidationResult validate(
            @PathVariable UUID objectTypeId,
            @RequestParam(required = false) UUID ownerId,
            @RequestBody(required = false) ValidateBindingRequest request
    ) {
        UUID resolved = BindingOwnerResolver.resolve(
                ownerId, request == null ? null : request.ownerId()
        );
        return bindings.validate(objectTypeId, resolved, request == null
                ? null
                : new ValidateBindingRequest(
                        resolved,
                        request.datasetKind(),
                        request.datasetId(),
                        request.mapping()
                ));
    }

    @PostMapping("/internal/v1/ontology/object-types/{objectTypeId}/binding/preview")
    public ObjectPreviewResponse preview(
            @PathVariable UUID objectTypeId,
            @RequestParam(required = false) UUID ownerId,
            @RequestBody(required = false) PreviewBindingRequest request
    ) {
        UUID resolved = BindingOwnerResolver.resolve(
                ownerId, request == null ? null : request.ownerId()
        );
        return previews.previewObject(objectTypeId, resolved, request == null
                ? null
                : new PreviewBindingRequest(
                        resolved,
                        request.datasetKind(),
                        request.datasetId(),
                        request.mapping(),
                        request.limit()
                ));
    }

    @PostMapping("/internal/v1/ontology/object-types/{objectTypeId}/binding/generate")
    public GenerateResponse generate(
            @PathVariable UUID objectTypeId,
            @RequestParam(required = false) UUID ownerId,
            @RequestBody(required = false) GenerateRequest request
    ) {
        UUID resolved = BindingOwnerResolver.resolve(
                ownerId, request == null ? null : request.ownerId()
        );
        if (request == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "mappingRevision 不能为空");
        }
        return bindings.generate(objectTypeId, new GenerateRequest(
                resolved, request.mappingRevision()
        ));
    }

    @GetMapping("/internal/v1/ontology/object-types/{objectTypeId}/binding/runs")
    public List<IndexRunResponse> runs(
            @PathVariable UUID objectTypeId,
            @RequestParam UUID ownerId
    ) {
        return runs.objectRuns(objectTypeId, ownerId);
    }
}
