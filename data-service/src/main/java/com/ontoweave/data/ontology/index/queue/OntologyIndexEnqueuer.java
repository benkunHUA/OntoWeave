package com.ontoweave.data.ontology.index.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.common.ValidationState;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyObjectVersionEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationVersionEntity;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexFailureCode;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import com.ontoweave.data.ontology.repository.OntologyObjectBindingRepository;
import com.ontoweave.data.ontology.repository.OntologyObjectVersionRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationBindingRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationDefinitionRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationVersionRepository;
import com.ontoweave.data.ontology.store.IndexEnqueuer;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OntologyIndexEnqueuer implements IndexEnqueuer {

    private final OntologyIndexRunRepository runs;
    private final OntologyObjectBindingRepository objectBindings;
    private final OntologyRelationBindingRepository relationBindings;
    private final OntologyObjectVersionRepository objectVersions;
    private final OntologyRelationVersionRepository relationVersions;
    private final OntologyRelationDefinitionRepository relationDefinitions;
    private final RunDatasetResolver datasets;
    private final IndexRequestValidator validator;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public OntologyIndexEnqueuer(
            OntologyIndexRunRepository runs,
            OntologyObjectBindingRepository objectBindings,
            OntologyRelationBindingRepository relationBindings,
            OntologyObjectVersionRepository objectVersions,
            OntologyRelationVersionRepository relationVersions,
            OntologyRelationDefinitionRepository relationDefinitions,
            RunDatasetResolver datasets,
            IndexRequestValidator validator,
            ObjectMapper mapper,
            JdbcTemplate jdbc
    ) {
        this.runs = runs;
        this.objectBindings = objectBindings;
        this.relationBindings = relationBindings;
        this.objectVersions = objectVersions;
        this.relationVersions = relationVersions;
        this.relationDefinitions = relationDefinitions;
        this.datasets = datasets;
        this.validator = validator;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public UUID enqueueManual(ManualIndexRequest request) {
        if (request == null || request.bindingId() == null) {
            throw new IndexBuildException(IndexFailureCode.MAPPING_INVALID, "binding is required");
        }
        return switch (request.subjectKind()) {
            case OBJECT_TYPE -> enqueueManualObject(request);
            case RELATION -> enqueueManualRelation(request);
        };
    }

    @Override
    @Transactional
    public UUID enqueueOnPublish(DatasetKind kind, UUID datasetId, UUID versionId) {
        DatasetRef dataset = datasets.require(kind, versionId, null);
        if (!dataset.datasetId().equals(datasetId)) {
            throw new IndexBuildException(
                    IndexFailureCode.DATASET_VERSION_MISMATCH,
                    "published dataset version belongs to another dataset"
            );
        }
        List<UUID> queued = new ArrayList<>();
        for (OntologyObjectBindingEntity binding : objectBindings
                .findByActiveDatasetKindAndActiveDatasetIdAndValidationState(
                        kind, datasetId, ValidationState.OK
                )) {
            queuePublishedObject(binding, dataset).ifPresent(queued::add);
        }
        for (OntologyRelationBindingEntity binding : relationBindings
                .findByActiveDatasetKindAndActiveDatasetIdAndValidationState(
                        kind, datasetId, ValidationState.OK
                )) {
            queuePublishedRelation(binding, dataset).ifPresent(queued::add);
        }
        return queued.isEmpty() ? null : queued.get(0);
    }

    @Override
    @Transactional
    public UUID enqueueRetry(UUID runId) {
        OntologyIndexRunEntity failed = runs.findById(runId).orElseThrow(() ->
                new IndexBuildException(IndexFailureCode.RUN_NOT_RETRYABLE, "run not found"));
        if (failed.getStatus() != RunStatus.FAILED) {
            throw new IndexBuildException(
                    IndexFailureCode.RUN_NOT_RETRYABLE,
                    "only failed runs can be retried"
            );
        }
        OntologyIndexRunEntity queued = new OntologyIndexRunEntity(
                UUID.randomUUID(),
                failed.getOwnerId(),
                failed.getSubjectKind(),
                failed.getSubjectId(),
                failed.getBindingId(),
                failed.getTargetDatasetKind(),
                failed.getTargetDatasetVersionId(),
                failed.getMappingSnapshot(),
                failed.getMappingRevision(),
                TriggerType.RETRY,
                failed.getId()
        );
        OntologyIndexRunEntity saved = runs.upsertQueued(queued);
        jdbc.update(
                "UPDATE data.ontology_index_run SET attempt = ? WHERE id = ? AND status = 'QUEUED'",
                failed.getAttempt() + 1,
                saved.getId()
        );
        return saved.getId();
    }

    private UUID enqueueManualObject(ManualIndexRequest request) {
        OntologyObjectBindingEntity binding = objectBindings.findById(request.bindingId())
                .filter(value -> value.getStatus() == OntologyObjectBindingEntity.BindingStatus.ACTIVE)
                .filter(value -> value.getOwnerId().equals(request.ownerId()))
                .filter(value -> value.getObjectTypeId().equals(request.subjectId()))
                .orElseThrow(() -> new IndexBuildException(
                        IndexFailureCode.MAPPING_INVALID, "object binding is unavailable"));
        DatasetRef dataset = datasets.require(
                request.datasetKind(), request.datasetVersionId(), request.ownerId());
        if (!dataset.datasetId().equals(binding.getDraftDatasetId())) {
            throw new IndexBuildException(
                    IndexFailureCode.DATASET_VERSION_MISMATCH,
                    "dataset version does not belong to the draft dataset"
            );
        }
        MappingValidationResult validation = validator.validateObject(
                request.mappingSnapshot(), dataset);
        requireValid(validation);
        return queue(request, binding.getId(), mappingNode(request.mappingSnapshot()));
    }

    private UUID enqueueManualRelation(ManualIndexRequest request) {
        OntologyRelationBindingEntity binding = relationBindings.findById(request.bindingId())
                .filter(value -> value.getStatus() == OntologyObjectBindingEntity.BindingStatus.ACTIVE)
                .filter(value -> value.getOwnerId().equals(request.ownerId()))
                .filter(value -> value.getRelationId().equals(request.subjectId()))
                .orElseThrow(() -> new IndexBuildException(
                        IndexFailureCode.MAPPING_INVALID, "relation binding is unavailable"));
        DatasetRef dataset = datasets.require(
                request.datasetKind(), request.datasetVersionId(), request.ownerId());
        if (!dataset.datasetId().equals(binding.getDraftDatasetId())) {
            throw new IndexBuildException(
                    IndexFailureCode.DATASET_VERSION_MISMATCH,
                    "dataset version does not belong to the draft dataset"
            );
        }
        OntologyRelationDefinitionEntity definition = relationDefinitions
                .findByRelationId(request.subjectId())
                .orElseThrow(() -> new IndexBuildException(
                        IndexFailureCode.MAPPING_INVALID, "relation definition is unavailable"));
        requireValid(validator.validateRelation(request.mappingSnapshot(), dataset, definition));
        return queue(request, binding.getId(), mappingNode(request.mappingSnapshot()));
    }

    private java.util.Optional<UUID> queuePublishedObject(
            OntologyObjectBindingEntity binding,
            DatasetRef dataset
    ) {
        if (binding.getStatus() != OntologyObjectBindingEntity.BindingStatus.ACTIVE
                || binding.getCurrentIndexVersionId() == null) {
            return java.util.Optional.empty();
        }
        OntologyObjectVersionEntity current = objectVersions
                .findById(binding.getCurrentIndexVersionId())
                .orElse(null);
        MappingSnapshot mapping = parseMapping(current == null ? null : current.getMappingSnapshot());
        MappingValidationResult validation = validator.validateObject(mapping, dataset);
        if (!validation.isValid()) {
            objectBindings.markNeedsRepair(binding.getId(), validationJson(validation));
            return java.util.Optional.empty();
        }
        OntologyIndexRunEntity candidate = new OntologyIndexRunEntity(
                UUID.randomUUID(),
                binding.getOwnerId(),
                SubjectKind.OBJECT_TYPE,
                binding.getObjectTypeId(),
                binding.getId(),
                dataset.kind(),
                dataset.versionId(),
                current.getMappingSnapshot(),
                current.getMappingRevision(),
                TriggerType.DATASET_PUBLISHED,
                null
        );
        return java.util.Optional.of(runs.upsertQueued(candidate).getId());
    }

    private java.util.Optional<UUID> queuePublishedRelation(
            OntologyRelationBindingEntity binding,
            DatasetRef dataset
    ) {
        if (binding.getStatus() != OntologyObjectBindingEntity.BindingStatus.ACTIVE
                || binding.getCurrentIndexVersionId() == null) {
            return java.util.Optional.empty();
        }
        OntologyRelationVersionEntity current = relationVersions
                .findById(binding.getCurrentIndexVersionId())
                .orElse(null);
        MappingSnapshot mapping = parseMapping(current == null ? null : current.getMappingSnapshot());
        OntologyRelationDefinitionEntity definition = relationDefinitions
                .findByRelationId(binding.getRelationId())
                .orElse(null);
        MappingValidationResult validation = validator.validateRelation(mapping, dataset, definition);
        if (!validation.isValid()) {
            relationBindings.markNeedsRepair(binding.getId(), validationJson(validation));
            return java.util.Optional.empty();
        }
        OntologyIndexRunEntity candidate = new OntologyIndexRunEntity(
                UUID.randomUUID(),
                binding.getOwnerId(),
                SubjectKind.RELATION,
                binding.getRelationId(),
                binding.getId(),
                dataset.kind(),
                dataset.versionId(),
                current.getMappingSnapshot(),
                current.getMappingRevision(),
                TriggerType.DATASET_PUBLISHED,
                null
        );
        return java.util.Optional.of(runs.upsertQueued(candidate).getId());
    }

    private UUID queue(ManualIndexRequest request, UUID bindingId, JsonNode mapping) {
        OntologyIndexRunEntity candidate = new OntologyIndexRunEntity(
                UUID.randomUUID(),
                request.ownerId(),
                request.subjectKind(),
                request.subjectId(),
                bindingId,
                request.datasetKind(),
                request.datasetVersionId(),
                mapping,
                request.mappingRevision(),
                TriggerType.MANUAL,
                null
        );
        return runs.upsertQueued(candidate).getId();
    }

    private void requireValid(MappingValidationResult validation) {
        if (!validation.isValid()) {
            throw new IndexBuildException(
                    IndexFailureCode.MAPPING_INVALID,
                    "mapping is invalid",
                    Map.of("samples", validation.blocking())
            );
        }
    }

    private MappingSnapshot parseMapping(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return mapper.treeToValue(node, MappingSnapshot.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private JsonNode mappingNode(MappingSnapshot mapping) {
        return mapper.valueToTree(mapping);
    }

    private String validationJson(MappingValidationResult validation) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "blocking", validation.blocking(),
                    "warnings", validation.warnings()
            ));
        } catch (JsonProcessingException exception) {
            return "{\"blocking\":[],\"warnings\":[]}";
        }
    }
}
