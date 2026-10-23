package com.ontoweave.data.ontology.index.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.ValidationState;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationVersionEntity;
import com.ontoweave.data.ontology.repository.OntologyRelationBindingRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationDefinitionRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationVersionRepository;
import com.ontoweave.data.ontology.index.queue.RunDatasetResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RelationIndexBuilder {

    private final OntologyRelationDefinitionRepository definitions;
    private final OntologyRelationBindingRepository bindings;
    private final OntologyRelationVersionRepository versions;
    private final RunDatasetResolver datasets;
    private final ObjectVersionResolver objectVersions;
    private final ManyToManyRelationBuilder manyToMany;
    private final ForeignKeyRelationBuilder foreignKeys;
    private final ObjectMapper mapper;

    public RelationIndexBuilder(
            OntologyRelationDefinitionRepository definitions,
            OntologyRelationBindingRepository bindings,
            OntologyRelationVersionRepository versions,
            RunDatasetResolver datasets,
            ObjectVersionResolver objectVersions,
            ManyToManyRelationBuilder manyToMany,
            ForeignKeyRelationBuilder foreignKeys,
            ObjectMapper mapper
    ) {
        this.definitions = definitions;
        this.bindings = bindings;
        this.versions = versions;
        this.datasets = datasets;
        this.objectVersions = objectVersions;
        this.manyToMany = manyToMany;
        this.foreignKeys = foreignKeys;
        this.mapper = mapper;
    }

    public RelationBuildBatch buildForObject(
            OntologyIndexRunEntity run,
            UUID objectVersionId,
            MappingSnapshot mapping,
            DatasetRef dataset,
            Runnable checkpoint
    ) {
        List<RelationBuildResult> built = new ArrayList<>();
        List<UUID> skipped = new ArrayList<>();
        for (OntologyRelationDefinitionEntity definition : affectedDefinitions(run)) {
            try {
                EndpointVersion from = endpoint(run, definition.getSourceTypeId(), objectVersionId,
                        mapping, dataset);
                EndpointVersion to = endpoint(run, definition.getTargetTypeId(), objectVersionId,
                        mapping, dataset);
                if (from == null || to == null) {
                    throw new RelationSkippedException(
                            "ENDPOINT_DEFINITION_MISSING",
                            "relation endpoint binding is unavailable"
                    );
                }
                if ("many_to_many".equals(definition.getCardinality())) {
                    built.add(buildManyToMany(run, definition, from, to, checkpoint));
                } else if (definition.getForeignKeyPropertyId() != null) {
                    built.add(foreignKeys.build(run.getId(), run.getOwnerId(), definition, from, to));
                } else {
                    throw new RelationSkippedException(
                            "RELATION_DEFINITION_INCOMPLETE",
                            "relation has no foreign key property"
                    );
                }
            } catch (RelationSkippedException exception) {
                skipped.add(definition.getRelationId());
            }
        }
        return new RelationBuildBatch(built, skipped);
    }

    private RelationBuildResult buildManyToMany(
            OntologyIndexRunEntity run,
            OntologyRelationDefinitionEntity definition,
            EndpointVersion from,
            EndpointVersion to,
            Runnable checkpoint
    ) {
        OntologyRelationBindingEntity binding = bindings
                .findByRelationIdAndOwnerId(definition.getRelationId(), run.getOwnerId())
                .filter(value -> value.getStatus() == OntologyObjectBindingEntity.BindingStatus.ACTIVE)
                .filter(value -> value.getValidationState() == ValidationState.OK)
                .filter(value -> value.getCurrentIndexVersionId() != null)
                .orElseThrow(() -> new RelationSkippedException(
                        "MAPPING_MISSING", "relation binding is unavailable"));
        OntologyRelationVersionEntity current = versions
                .findById(binding.getCurrentIndexVersionId())
                .orElseThrow(() -> new RelationSkippedException(
                        "MAPPING_MISSING", "current relation version is unavailable"));
        MappingSnapshot mapping = parse(current.getMappingSnapshot());
        DatasetRef dataset = datasets.find(
                        current.getSourceDatasetKind(), current.getSourceDatasetVersionId())
                .orElseThrow(() -> new RelationSkippedException(
                        "SNAPSHOT_MISSING", "relation dataset snapshot is unavailable"));
        return manyToMany.build(
                run.getId(),
                run.getOwnerId(),
                binding.getId(),
                definition,
                mapping,
                current.getMappingSnapshot(),
                current.getMappingRevision() == null ? 0L : current.getMappingRevision(),
                dataset,
                from,
                to,
                checkpoint
        );
    }

    private EndpointVersion endpoint(
            OntologyIndexRunEntity run,
            UUID objectTypeId,
            UUID objectVersionId,
            MappingSnapshot mapping,
            DatasetRef dataset
    ) {
        return objectVersions.resolve(
                run.getOwnerId(),
                objectTypeId,
                run.getSubjectId(),
                objectVersionId,
                mapping,
                run.getMappingSnapshot(),
                run.getMappingRevision(),
                dataset.kind(),
                dataset.datasetId(),
                dataset.versionId(),
                dataset.snapshotId()
        ).orElse(null);
    }

    private List<OntologyRelationDefinitionEntity> affectedDefinitions(OntologyIndexRunEntity run) {
        if (run.getSubjectKind() != SubjectKind.OBJECT_TYPE) {
            return List.of();
        }
        Map<UUID, OntologyRelationDefinitionEntity> unique = new LinkedHashMap<>();
        definitions.findByOwnerIdAndSourceTypeId(run.getOwnerId(), run.getSubjectId())
                .forEach(value -> unique.put(value.getRelationId(), value));
        definitions.findByOwnerIdAndTargetTypeId(run.getOwnerId(), run.getSubjectId())
                .forEach(value -> unique.put(value.getRelationId(), value));
        return List.copyOf(unique.values());
    }

    private MappingSnapshot parse(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return mapper.treeToValue(node, MappingSnapshot.class);
        } catch (Exception exception) {
            return null;
        }
    }
}
