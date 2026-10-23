package com.ontoweave.data.ontology.binding.service;

import com.ontoweave.data.ontology.binding.api.dto.GenerateRequest;
import com.ontoweave.data.ontology.binding.api.dto.GenerateResponse;
import com.ontoweave.data.ontology.binding.api.dto.MappingSaveRequest;
import com.ontoweave.data.ontology.binding.api.dto.MappingSaveResponse;
import com.ontoweave.data.ontology.binding.api.dto.RelationBindingResponse;
import com.ontoweave.data.ontology.binding.api.dto.ValidateBindingRequest;
import com.ontoweave.data.ontology.binding.support.BindingErrors;
import com.ontoweave.data.ontology.binding.support.BindingJson;
import com.ontoweave.data.ontology.binding.support.BindingOccupancyGuard;
import com.ontoweave.data.ontology.binding.validation.BindingCheckService;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.ValidationState;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.repository.OntologyRelationBindingRepository;
import com.ontoweave.data.ontology.store.IndexEnqueuer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RelationBindingService {

    private final OntologyRelationBindingRepository bindings;
    private final RelationDefinitionService definitions;
    private final BindingCheckService checks;
    private final BindingOccupancyGuard occupancy;
    private final BindingJson json;
    private final BindingRunService runs;
    private final ObjectProvider<IndexEnqueuer> enqueuers;
    private final JdbcTemplate jdbc;

    public RelationBindingService(
            OntologyRelationBindingRepository bindings,
            RelationDefinitionService definitions,
            BindingCheckService checks,
            BindingOccupancyGuard occupancy,
            BindingJson json,
            BindingRunService runs,
            ObjectProvider<IndexEnqueuer> enqueuers,
            JdbcTemplate jdbc
    ) {
        this.bindings = bindings;
        this.definitions = definitions;
        this.checks = checks;
        this.occupancy = occupancy;
        this.json = json;
        this.runs = runs;
        this.enqueuers = enqueuers;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public RelationBindingResponse get(UUID relationId, UUID ownerId) {
        return response(require(relationId, ownerId));
    }

    @Transactional
    public MappingSaveResponse save(UUID relationId, MappingSaveRequest request) {
        validateSaveRequest(request);
        OntologyRelationDefinitionEntity definition = bindableDefinition(relationId, request.ownerId());
        long nextRevision = request.baseRevision() == 0 ? 1 : request.baseRevision() + 1;
        MappingSnapshot mapping = ObjectBindingService.withRevision(request.mapping(), nextRevision);
        BindingCheckService.CheckResult check = checks.checkRelation(
                request.datasetKind(),
                request.datasetId(),
                request.ownerId(),
                mapping,
                definition
        );
        occupancy.requireAvailable(
                request.datasetKind(), request.datasetId(), null, relationId
        );
        if (request.baseRevision() == 0) {
            return create(relationId, request, mapping, check.validation());
        }
        return update(relationId, request, mapping, check.validation());
    }

    @Transactional(readOnly = true)
    public MappingValidationResult validate(
            UUID relationId,
            UUID ownerId,
            ValidateBindingRequest request
    ) {
        OntologyRelationBindingEntity binding = require(relationId, ownerId);
        OntologyRelationDefinitionEntity definition = bindableDefinition(relationId, ownerId);
        MappingSnapshot mapping = request != null && request.mapping() != null
                ? ObjectBindingService.withRevision(request.mapping(), binding.getRevision())
                : json.mapping(binding.getDraftMapping());
        var kind = request != null && request.datasetKind() != null
                ? request.datasetKind()
                : binding.getDraftDatasetKind();
        UUID datasetId = request != null && request.datasetId() != null
                ? request.datasetId()
                : binding.getDraftDatasetId();
        return checks.checkRelation(kind, datasetId, ownerId, mapping, definition).validation();
    }

    @Transactional
    public boolean unbind(UUID relationId, UUID ownerId) {
        OntologyRelationBindingEntity binding = bindings
                .findByRelationIdAndOwnerId(relationId, ownerId)
                .orElse(null);
        if (binding == null) {
            return false;
        }
        bindings.unbind(binding.getId());
        jdbc.update("""
                UPDATE data.ontology_relation_version
                SET status = 'RETIRED', retired_at = now()
                WHERE binding_id = ? AND status = 'ACTIVE'
                """, binding.getId());
        return true;
    }

    @Transactional
    public GenerateResponse generate(UUID relationId, GenerateRequest request) {
        UUID ownerId = requireOwner(request == null ? null : request.ownerId());
        OntologyRelationBindingEntity binding = requireActive(relationId, ownerId);
        OntologyRelationDefinitionEntity definition = bindableDefinition(relationId, ownerId);
        MappingSnapshot mapping = json.mapping(binding.getDraftMapping());
        if (request.mappingRevision() != binding.getRevision()) {
            throw BindingErrors.revisionConflict(binding.getRevision(), mapping);
        }
        BindingCheckService.CheckResult check = checks.checkRelation(
                binding.getDraftDatasetKind(),
                binding.getDraftDatasetId(),
                ownerId,
                mapping,
                definition
        );
        if (!check.validation().isValid()) {
            throw BindingErrors.mappingInvalid(check.validation());
        }
        IndexEnqueuer enqueuer = enqueuers.getIfAvailable();
        if (enqueuer == null) {
            throw BindingErrors.indexEnqueuerUnavailable();
        }
        UUID runId = enqueuer.enqueueManual(new IndexEnqueuer.ManualIndexRequest(
                ownerId,
                SubjectKind.RELATION,
                relationId,
                binding.getId(),
                binding.getDraftDatasetKind(),
                check.dataset().versionId(),
                mapping,
                binding.getRevision()
        ));
        return new GenerateResponse(runId);
    }

    public OntologyRelationBindingEntity require(UUID relationId, UUID ownerId) {
        return bindings.findByRelationIdAndOwnerId(relationId, ownerId)
                .orElseThrow(BindingErrors::bindingNotFound);
    }

    OntologyRelationBindingEntity requireActive(UUID relationId, UUID ownerId) {
        OntologyRelationBindingEntity binding = require(relationId, ownerId);
        if (binding.getStatus() != OntologyObjectBindingEntity.BindingStatus.ACTIVE) {
            throw BindingErrors.bindingNotFound();
        }
        return binding;
    }

    RelationBindingResponse response(OntologyRelationBindingEntity binding) {
        return new RelationBindingResponse(
                binding.getId(),
                binding.getOwnerId(),
                binding.getRelationId(),
                binding.getStatus().name(),
                binding.getRevision(),
                binding.getDraftDatasetKind(),
                binding.getDraftDatasetId(),
                json.mapping(binding.getDraftMapping()),
                binding.getActiveDatasetKind(),
                binding.getActiveDatasetId(),
                binding.getActiveDatasetVersionId(),
                binding.getActiveMappingRevision(),
                binding.getCurrentIndexVersionId(),
                binding.getValidationState(),
                json.validation(binding.getValidationError()),
                runs.recentRelationRun(binding.getRelationId(), binding.getOwnerId())
        );
    }

    private OntologyRelationDefinitionEntity bindableDefinition(UUID relationId, UUID ownerId) {
        OntologyRelationDefinitionEntity definition = definitions.require(relationId, ownerId);
        if (!RelationDefinitionService.MANY_TO_MANY.equals(definition.getCardinality())) {
            throw BindingErrors.relationNotBindable();
        }
        return definition;
    }

    private MappingSaveResponse create(
            UUID relationId,
            MappingSaveRequest request,
            MappingSnapshot mapping,
            MappingValidationResult validation
    ) {
        OntologyRelationBindingEntity existing = bindings.findByRelationId(relationId).orElse(null);
        if (existing != null) {
            if (!existing.getOwnerId().equals(request.ownerId())) {
                throw BindingErrors.ownerMismatch();
            }
            throw BindingErrors.revisionConflict(
                    existing.getRevision(), json.mapping(existing.getDraftMapping())
            );
        }
        OntologyRelationBindingEntity created = new OntologyRelationBindingEntity(
                UUID.randomUUID(),
                request.ownerId(),
                relationId,
                request.datasetKind(),
                request.datasetId(),
                json.node(mapping)
        );
        try {
            bindings.saveAndFlush(created);
        } catch (DataIntegrityViolationException exception) {
            OntologyRelationBindingEntity concurrent = bindings.findByRelationId(relationId)
                    .orElseThrow(() -> exception);
            throw BindingErrors.revisionConflict(
                    concurrent.getRevision(), json.mapping(concurrent.getDraftMapping())
            );
        }
        saveValidation(created.getId(), validation);
        return new MappingSaveResponse(created.getId(), created.getRevision(), mapping, validation);
    }

    private MappingSaveResponse update(
            UUID relationId,
            MappingSaveRequest request,
            MappingSnapshot mapping,
            MappingValidationResult validation
    ) {
        OntologyRelationBindingEntity existing = bindings.findByRelationId(relationId)
                .orElseThrow(BindingErrors::bindingNotFound);
        if (!existing.getOwnerId().equals(request.ownerId())) {
            throw BindingErrors.ownerMismatch();
        }
        ValidationState state = validation.isValid()
                ? ValidationState.OK
                : ValidationState.NEEDS_REPAIR;
        int updated = bindings.updateDraft(
                existing.getId(),
                request.baseRevision(),
                request.datasetKind(),
                request.datasetId(),
                json.node(mapping).toString(),
                state,
                json.node(validation).toString()
        );
        if (updated == 0) {
            OntologyRelationBindingEntity current = bindings.findById(existing.getId())
                    .orElseThrow(BindingErrors::bindingNotFound);
            throw BindingErrors.revisionConflict(
                    current.getRevision(), json.mapping(current.getDraftMapping())
            );
        }
        OntologyRelationBindingEntity saved = bindings.findById(existing.getId())
                .orElseThrow(BindingErrors::bindingNotFound);
        return new MappingSaveResponse(saved.getId(), saved.getRevision(), mapping, validation);
    }

    private void saveValidation(UUID bindingId, MappingValidationResult validation) {
        jdbc.update("""
                UPDATE data.ontology_relation_binding
                SET validation_state = ?, validation_error = CAST(? AS jsonb)
                WHERE id = ?
                """, validation.isValid() ? "OK" : "NEEDS_REPAIR",
                json.node(validation).toString(), bindingId);
    }

    private void validateSaveRequest(MappingSaveRequest request) {
        if (request == null || request.ownerId() == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "ownerId 不能为空");
        }
        if (request.mapping() == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "mapping 不能为空");
        }
        if (request.datasetKind() == null || request.datasetId() == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "datasetKind/datasetId 不能为空");
        }
        if (request.baseRevision() < 0) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "baseRevision 不能为负数");
        }
    }

    private UUID requireOwner(UUID ownerId) {
        if (ownerId == null) {
            throw BindingErrors.badRequest("VALIDATION_ERROR", "ownerId 不能为空");
        }
        return ownerId;
    }
}
