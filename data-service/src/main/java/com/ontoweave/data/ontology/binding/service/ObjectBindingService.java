package com.ontoweave.data.ontology.binding.service;

import com.ontoweave.data.ontology.binding.api.dto.GenerateRequest;
import com.ontoweave.data.ontology.binding.api.dto.GenerateResponse;
import com.ontoweave.data.ontology.binding.api.dto.MappingSaveRequest;
import com.ontoweave.data.ontology.binding.api.dto.MappingSaveResponse;
import com.ontoweave.data.ontology.binding.api.dto.ObjectBindingResponse;
import com.ontoweave.data.ontology.binding.api.dto.ValidateBindingRequest;
import com.ontoweave.data.ontology.binding.support.BindingErrors;
import com.ontoweave.data.ontology.binding.support.BindingJson;
import com.ontoweave.data.ontology.binding.support.BindingOccupancyGuard;
import com.ontoweave.data.ontology.binding.validation.BindingCheckService;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.ValidationState;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.repository.OntologyObjectBindingRepository;
import com.ontoweave.data.ontology.store.IndexEnqueuer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ObjectBindingService {

    private final OntologyObjectBindingRepository bindings;
    private final BindingCheckService checks;
    private final BindingOccupancyGuard occupancy;
    private final BindingJson json;
    private final BindingRunService runs;
    private final ObjectProvider<IndexEnqueuer> enqueuers;
    private final JdbcTemplate jdbc;

    public ObjectBindingService(
            OntologyObjectBindingRepository bindings,
            BindingCheckService checks,
            BindingOccupancyGuard occupancy,
            BindingJson json,
            BindingRunService runs,
            ObjectProvider<IndexEnqueuer> enqueuers,
            JdbcTemplate jdbc
    ) {
        this.bindings = bindings;
        this.checks = checks;
        this.occupancy = occupancy;
        this.json = json;
        this.runs = runs;
        this.enqueuers = enqueuers;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ObjectBindingResponse get(UUID objectTypeId, UUID ownerId) {
        OntologyObjectBindingEntity binding = require(objectTypeId, ownerId);
        return response(binding);
    }

    @Transactional
    public MappingSaveResponse save(UUID objectTypeId, MappingSaveRequest request) {
        validateSaveRequest(request);
        long nextRevision = request.baseRevision() == 0 ? 1 : request.baseRevision() + 1;
        MappingSnapshot mapping = withRevision(request.mapping(), nextRevision);
        BindingCheckService.CheckResult check = checks.checkObject(
                request.datasetKind(), request.datasetId(), request.ownerId(), mapping
        );
        occupancy.requireAvailable(
                request.datasetKind(), request.datasetId(), objectTypeId, null
        );
        if (request.baseRevision() == 0) {
            return create(objectTypeId, request, mapping, check.validation());
        }
        return update(objectTypeId, request, mapping, check.validation());
    }

    @Transactional(readOnly = true)
    public MappingValidationResult validate(
            UUID objectTypeId,
            UUID ownerId,
            ValidateBindingRequest request
    ) {
        OntologyObjectBindingEntity binding = require(objectTypeId, ownerId);
        DatasetKind kind = request != null && request.datasetKind() != null
                ? request.datasetKind()
                : binding.getDraftDatasetKind();
        UUID datasetId = request != null && request.datasetId() != null
                ? request.datasetId()
                : binding.getDraftDatasetId();
        MappingSnapshot mapping = request != null && request.mapping() != null
                ? withRevision(request.mapping(), binding.getRevision())
                : json.mapping(binding.getDraftMapping());
        return checks.checkObject(kind, datasetId, ownerId, mapping).validation();
    }

    @Transactional
    public boolean unbind(UUID objectTypeId, UUID ownerId) {
        OntologyObjectBindingEntity binding = bindings
                .findByObjectTypeIdAndOwnerId(objectTypeId, ownerId)
                .orElse(null);
        if (binding == null) {
            return false;
        }
        bindings.unbind(binding.getId());
        jdbc.update("""
                UPDATE data.ontology_object_version
                SET status = 'RETIRED', retired_at = now()
                WHERE binding_id = ? AND status = 'ACTIVE'
                """, binding.getId());
        jdbc.update("""
                UPDATE data.ontology_relation_version
                SET status = 'RETIRED', retired_at = now()
                WHERE status = 'ACTIVE'
                  AND (from_object_type_id = ? OR to_object_type_id = ?)
                """, objectTypeId, objectTypeId);
        return true;
    }

    @Transactional
    public GenerateResponse generate(UUID objectTypeId, GenerateRequest request) {
        UUID ownerId = requireOwner(request == null ? null : request.ownerId());
        OntologyObjectBindingEntity binding = requireActive(objectTypeId, ownerId);
        MappingSnapshot mapping = json.mapping(binding.getDraftMapping());
        if (request.mappingRevision() != binding.getRevision()) {
            throw BindingErrors.revisionConflict(binding.getRevision(), mapping);
        }
        BindingCheckService.CheckResult check = checks.checkObject(
                binding.getDraftDatasetKind(),
                binding.getDraftDatasetId(),
                ownerId,
                mapping
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
                SubjectKind.OBJECT_TYPE,
                objectTypeId,
                binding.getId(),
                binding.getDraftDatasetKind(),
                check.dataset().versionId(),
                mapping,
                binding.getRevision()
        ));
        return new GenerateResponse(runId);
    }

    public OntologyObjectBindingEntity require(UUID objectTypeId, UUID ownerId) {
        return bindings.findByObjectTypeIdAndOwnerId(objectTypeId, ownerId)
                .orElseThrow(BindingErrors::bindingNotFound);
    }

    OntologyObjectBindingEntity requireActive(UUID objectTypeId, UUID ownerId) {
        OntologyObjectBindingEntity binding = require(objectTypeId, ownerId);
        if (binding.getStatus() != OntologyObjectBindingEntity.BindingStatus.ACTIVE) {
            throw BindingErrors.bindingNotFound();
        }
        return binding;
    }

    ObjectBindingResponse response(OntologyObjectBindingEntity binding) {
        return new ObjectBindingResponse(
                binding.getId(),
                binding.getOwnerId(),
                binding.getObjectTypeId(),
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
                runs.recentObjectRun(binding.getObjectTypeId(), binding.getOwnerId())
        );
    }

    public static MappingSnapshot withRevision(MappingSnapshot mapping, long revision) {
        return new MappingSnapshot(
                mapping.primaryKeyAttributeId(),
                mapping.titleKeyAttributeId(),
                mapping.attributes(),
                mapping.fromKeyColumn(),
                mapping.fromKeyDatatype(),
                mapping.toKeyColumn(),
                mapping.toKeyDatatype(),
                revision
        );
    }

    private MappingSaveResponse create(
            UUID objectTypeId,
            MappingSaveRequest request,
            MappingSnapshot mapping,
            MappingValidationResult validation
    ) {
        OntologyObjectBindingEntity existing = bindings.findByObjectTypeId(objectTypeId).orElse(null);
        if (existing != null) {
            if (!existing.getOwnerId().equals(request.ownerId())) {
                throw BindingErrors.ownerMismatch();
            }
            throw BindingErrors.revisionConflict(
                    existing.getRevision(), json.mapping(existing.getDraftMapping())
            );
        }
        OntologyObjectBindingEntity created = new OntologyObjectBindingEntity(
                UUID.randomUUID(),
                request.ownerId(),
                objectTypeId,
                request.datasetKind(),
                request.datasetId(),
                json.node(mapping)
        );
        try {
            bindings.saveAndFlush(created);
        } catch (DataIntegrityViolationException exception) {
            OntologyObjectBindingEntity concurrent = bindings.findByObjectTypeId(objectTypeId)
                    .orElseThrow(() -> exception);
            throw BindingErrors.revisionConflict(
                    concurrent.getRevision(), json.mapping(concurrent.getDraftMapping())
            );
        }
        saveValidation(created.getId(), validation);
        return new MappingSaveResponse(created.getId(), created.getRevision(), mapping, validation);
    }

    private MappingSaveResponse update(
            UUID objectTypeId,
            MappingSaveRequest request,
            MappingSnapshot mapping,
            MappingValidationResult validation
    ) {
        OntologyObjectBindingEntity existing = bindings.findByObjectTypeId(objectTypeId)
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
            OntologyObjectBindingEntity current = bindings.findById(existing.getId())
                    .orElseThrow(BindingErrors::bindingNotFound);
            throw BindingErrors.revisionConflict(
                    current.getRevision(), json.mapping(current.getDraftMapping())
            );
        }
        OntologyObjectBindingEntity saved = bindings.findById(existing.getId())
                .orElseThrow(BindingErrors::bindingNotFound);
        return new MappingSaveResponse(saved.getId(), saved.getRevision(), mapping, validation);
    }

    private void saveValidation(UUID bindingId, MappingValidationResult validation) {
        jdbc.update("""
                UPDATE data.ontology_object_binding
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
