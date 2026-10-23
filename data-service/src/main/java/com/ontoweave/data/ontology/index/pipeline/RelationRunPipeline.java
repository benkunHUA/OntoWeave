package com.ontoweave.data.ontology.index.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.ValidationState;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexFailureCode;
import com.ontoweave.data.ontology.index.queue.RunDatasetResolver;
import com.ontoweave.data.ontology.repository.OntologyRelationBindingRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationDefinitionRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RelationRunPipeline {

    private final RunDatasetResolver datasets;
    private final OntologyRelationDefinitionRepository definitions;
    private final OntologyRelationBindingRepository bindings;
    private final ObjectVersionResolver objectVersions;
    private final ManyToManyRelationBuilder manyToMany;
    private final ObjectMapper mapper;

    public RelationRunPipeline(
            RunDatasetResolver datasets,
            OntologyRelationDefinitionRepository definitions,
            OntologyRelationBindingRepository bindings,
            ObjectVersionResolver objectVersions,
            ManyToManyRelationBuilder manyToMany,
            ObjectMapper mapper
    ) {
        this.datasets = datasets;
        this.definitions = definitions;
        this.bindings = bindings;
        this.objectVersions = objectVersions;
        this.manyToMany = manyToMany;
        this.mapper = mapper;
    }

    public RelationRunResult execute(OntologyIndexRunEntity run, Runnable checkpoint) {
        DatasetRef dataset = datasets.require(
                run.getTargetDatasetKind(), run.getTargetDatasetVersionId(), run.getOwnerId());
        MappingSnapshot mapping = parse(run);
        OntologyRelationDefinitionEntity definition = definitions
                .findByRelationId(run.getSubjectId())
                .orElse(null);
        OntologyRelationBindingEntity binding = bindings
                .findByRelationIdAndOwnerId(run.getSubjectId(), run.getOwnerId())
                .filter(value -> value.getStatus() == OntologyObjectBindingEntity.BindingStatus.ACTIVE)
                .filter(value -> value.getValidationState() == ValidationState.OK)
                .orElse(null);
        if (definition == null || binding == null || mapping == null) {
            return skipped("RELATION_DEFINITION_MISSING");
        }
        EndpointVersion from = objectVersions.resolve(
                run.getOwnerId(), definition.getSourceTypeId(), null, null, null, null, 0L,
                null, null, null, 0L
        ).orElse(null);
        EndpointVersion to = objectVersions.resolve(
                run.getOwnerId(), definition.getTargetTypeId(), null, null, null, null, 0L,
                null, null, null, 0L
        ).orElse(null);
        if (from == null || to == null) {
            return skipped("ENDPOINT_DEFINITION_MISSING");
        }
        try {
            RelationBuildResult result = manyToMany.build(
                    run.getId(),
                    run.getOwnerId(),
                    binding.getId(),
                    definition,
                    mapping,
                    run.getMappingSnapshot(),
                    run.getMappingRevision(),
                    dataset,
                    from,
                    to,
                    checkpoint
            );
            return new RelationRunResult(result, Map.of(
                    "relationId", definition.getRelationId(),
                    "rowCount", result.rowCount(),
                    "skippedCount", result.skippedCount()
            ));
        } catch (RelationSkippedException exception) {
            return skipped(exception.reason());
        }
    }

    private RelationRunResult skipped(String reason) {
        return new RelationRunResult(null, Map.of(
                "rowCount", 0,
                "skippedCount", 1,
                "skipReason", reason
        ));
    }

    private MappingSnapshot parse(OntologyIndexRunEntity run) {
        if (run.getMappingSnapshot() == null || run.getMappingSnapshot().isNull()) {
            return null;
        }
        try {
            return mapper.treeToValue(run.getMappingSnapshot(), MappingSnapshot.class);
        } catch (Exception exception) {
            throw new IndexBuildException(
                    IndexFailureCode.MAPPING_INVALID,
                    "run mapping snapshot is invalid",
                    Map.of(),
                    exception
            );
        }
    }
}
