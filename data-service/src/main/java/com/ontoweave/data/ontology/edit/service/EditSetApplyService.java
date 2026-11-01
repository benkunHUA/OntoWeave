package com.ontoweave.data.ontology.edit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.ontology.edit.model.EditSetPreview;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.model.EditSetResult;
import com.ontoweave.data.ontology.edit.store.JdbcEditAuditStore;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EditSetApplyService {

    private final JdbcEditAuditStore audit;
    private final EditSetPreviewService previewService;
    private final ObjectEditApplier objectApplier;
    private final RelationEditApplier relationApplier;
    private final EditContextResolver contexts;
    private final ObjectMapper mapper;

    public EditSetApplyService(
            JdbcEditAuditStore audit,
            EditSetPreviewService previewService,
            ObjectEditApplier objectApplier,
            RelationEditApplier relationApplier,
            EditContextResolver contexts,
            ObjectMapper mapper
    ) {
        this.audit = audit;
        this.previewService = previewService;
        this.objectApplier = objectApplier;
        this.relationApplier = relationApplier;
        this.contexts = contexts;
        this.mapper = mapper;
    }

    @Transactional
    public EditSetResult apply(EditSetRequest request) {
        validateRequest(request);
        var existing = audit.findApplied(request.ownerId(), request.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }
        EditSetPreview preview = previewService.preview(request);
        if (!preview.applicable()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EDIT_CONFLICT",
                    "edit set 不可应用",
                    Map.of("conflicts", preview.conflicts())
            );
        }

        List<EditSetAuditChange> changes = new ArrayList<>();
        List<EditSetResult.ObjectResult> objectResults = new ArrayList<>();
        List<EditSetResult.RelationResult> relationResults = new ArrayList<>();
        request.objectCreates().forEach(item ->
                objectResults.add(objectApplier.create(request, item, changes)));
        request.objectUpdates().forEach(item ->
                objectResults.add(objectApplier.update(request, item, changes)));
        request.objectDeletes().forEach(item ->
                objectResults.add(objectApplier.delete(request, item, changes)));
        request.relationCreates().forEach(item -> relationResults.add(
                relationApplier.apply(request, item.relationId(), item.fromPrimaryKey(),
                        item.toPrimaryKey(), true, changes)));
        request.relationDeletes().forEach(item -> relationResults.add(
                relationApplier.apply(request, item.relationId(), item.fromPrimaryKey(),
                        item.toPrimaryKey(), false, changes)));

        EditSetResult result = new EditSetResult(
                request.executionId(), objectResults, relationResults
        );
        audit.insertExecution(request, baseVersions(request), mapper.valueToTree(result));
        int sequence = 1;
        for (EditSetAuditChange change : changes) {
            audit.insertChange(
                    request.executionId(), sequence++, change.kind(), change.objectTypeId(),
                    change.primaryKey(), change.attributeId(), change.relationId(),
                    change.fromTypeId(), change.fromPrimaryKey(), change.toTypeId(),
                    change.toPrimaryKey(), change.before(), change.after()
            );
        }
        return result;
    }

    private JsonNode baseVersions(EditSetRequest request) {
        Map<String, Object> versions = new LinkedHashMap<>();
        request.objectCreates().forEach(item ->
                putVersion(request, versions, item.objectTypeId()));
        request.objectUpdates().forEach(item ->
                putVersion(request, versions, item.objectTypeId()));
        request.objectDeletes().forEach(item ->
                putVersion(request, versions, item.objectTypeId()));
        return mapper.valueToTree(versions);
    }

    private void putVersion(
            EditSetRequest request,
            Map<String, Object> versions,
            UUID objectTypeId
    ) {
        if (objectTypeId != null) {
            versions.putIfAbsent(
                    objectTypeId.toString(),
                    contexts.require(request.ownerId(), objectTypeId).versionId()
            );
        }
    }

    private void validateRequest(EditSetRequest request) {
        if (request == null || request.executionId() == null || request.ownerId() == null
                || request.graphId() == null || request.actionTypeId() == null
                || request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw QueryErrors.invalidRequest(
                    "edit set 缺少 executionId、ownerId、graphId、actionTypeId 或幂等键");
        }
    }
}
