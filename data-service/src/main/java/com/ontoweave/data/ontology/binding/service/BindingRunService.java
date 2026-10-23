package com.ontoweave.data.ontology.binding.service;

import com.ontoweave.data.ontology.binding.api.dto.IndexRunResponse;
import com.ontoweave.data.ontology.binding.api.dto.RelationVersionSummary;
import com.ontoweave.data.ontology.binding.support.BindingErrors;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationVersionEntity;
import com.ontoweave.data.ontology.repository.OntologyIndexRunRepository;
import com.ontoweave.data.ontology.repository.OntologyObjectBindingRepository;
import com.ontoweave.data.ontology.repository.OntologyRelationVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BindingRunService {

    private final OntologyIndexRunRepository runs;
    private final OntologyObjectBindingRepository objectBindings;
    private final OntologyRelationVersionRepository relationVersions;
    private final RelationDefinitionService definitions;

    public BindingRunService(
            OntologyIndexRunRepository runs,
            OntologyObjectBindingRepository objectBindings,
            OntologyRelationVersionRepository relationVersions,
            RelationDefinitionService definitions
    ) {
        this.runs = runs;
        this.objectBindings = objectBindings;
        this.relationVersions = relationVersions;
        this.definitions = definitions;
    }

    @Transactional(readOnly = true)
    public List<IndexRunResponse> objectRuns(UUID objectTypeId, UUID ownerId) {
        objectBindings.findByObjectTypeIdAndOwnerId(objectTypeId, ownerId)
                .orElseThrow(BindingErrors::bindingNotFound);
        return runs.findBySubjectKindAndSubjectIdOrderByRequestedAtDesc(
                        SubjectKind.OBJECT_TYPE, objectTypeId
                ).stream()
                .filter(run -> ownerId.equals(run.getOwnerId()))
                .map(IndexRunResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IndexRunResponse> relationRuns(UUID relationId, UUID ownerId) {
        definitions.require(relationId, ownerId);
        Map<UUID, OntologyIndexRunEntity> byId = new LinkedHashMap<>();
        runs.findBySubjectKindAndSubjectIdOrderByRequestedAtDesc(
                        SubjectKind.RELATION, relationId
                ).stream()
                .filter(run -> ownerId.equals(run.getOwnerId()))
                .forEach(run -> byId.put(run.getId(), run));
        for (OntologyRelationVersionEntity version : relationVersions
                .findByRelationIdOrderByCreatedAtDesc(relationId)) {
            if (!ownerId.equals(version.getOwnerId())) {
                continue;
            }
            runs.findById(version.getRunId())
                    .filter(run -> ownerId.equals(run.getOwnerId()))
                    .ifPresent(run -> byId.putIfAbsent(run.getId(), run));
        }
        List<OntologyIndexRunEntity> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.comparing(
                OntologyIndexRunEntity::getRequestedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        return sorted.stream().map(IndexRunResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public IndexRunResponse recentObjectRun(UUID objectTypeId, UUID ownerId) {
        return objectRuns(objectTypeId, ownerId).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public IndexRunResponse recentRelationRun(UUID relationId, UUID ownerId) {
        return relationRuns(relationId, ownerId).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public RelationVersionSummary latestRelationVersion(UUID relationId, UUID ownerId) {
        return relationVersions.findByRelationIdOrderByCreatedAtDesc(relationId).stream()
                .filter(version -> ownerId.equals(version.getOwnerId()))
                .findFirst()
                .map(this::summary)
                .orElse(null);
    }

    private RelationVersionSummary summary(OntologyRelationVersionEntity version) {
        return new RelationVersionSummary(
                version.getId(),
                version.getRelationKind(),
                version.getStatus(),
                version.getRowCount() == null ? 0 : version.getRowCount(),
                version.getSkippedCount(),
                version.getRunId(),
                version.getActivatedAt()
        );
    }
}
