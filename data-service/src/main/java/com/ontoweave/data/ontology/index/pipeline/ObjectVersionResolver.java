package com.ontoweave.data.ontology.index.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyObjectVersionEntity;
import com.ontoweave.data.ontology.repository.OntologyObjectBindingRepository;
import com.ontoweave.data.ontology.repository.OntologyObjectVersionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ObjectVersionResolver {

    private final OntologyObjectBindingRepository bindings;
    private final OntologyObjectVersionRepository versions;
    private final ObjectMapper mapper;

    public ObjectVersionResolver(
            OntologyObjectBindingRepository bindings,
            OntologyObjectVersionRepository versions,
            ObjectMapper mapper
    ) {
        this.bindings = bindings;
        this.versions = versions;
        this.mapper = mapper;
    }

    public Optional<EndpointVersion> resolve(
            UUID ownerId,
            UUID objectTypeId,
            UUID rebuiltTypeId,
            UUID rebuiltVersionId,
            MappingSnapshot rebuiltMapping,
            JsonNode rebuiltMappingNode,
            long rebuiltMappingRevision,
            DatasetKind rebuiltDatasetKind,
            UUID rebuiltDatasetId,
            UUID rebuiltDatasetVersionId,
            long rebuiltSnapshotId
    ) {
        if (objectTypeId.equals(rebuiltTypeId)) {
            return Optional.of(new EndpointVersion(
                    objectTypeId,
                    rebuiltVersionId,
                    rebuiltMapping,
                    rebuiltMappingNode,
                    rebuiltMappingRevision,
                    rebuiltDatasetKind,
                    rebuiltDatasetId,
                    rebuiltDatasetVersionId,
                    rebuiltSnapshotId
            ));
        }
        OntologyObjectBindingEntity binding = bindings
                .findByObjectTypeIdAndOwnerId(objectTypeId, ownerId)
                .filter(value -> value.getStatus() == OntologyObjectBindingEntity.BindingStatus.ACTIVE)
                .filter(value -> value.getCurrentIndexVersionId() != null)
                .orElse(null);
        if (binding == null) {
            return Optional.empty();
        }
        OntologyObjectVersionEntity version = versions
                .findById(binding.getCurrentIndexVersionId())
                .orElse(null);
        if (version == null) {
            return Optional.empty();
        }
        MappingSnapshot mapping = parse(version.getMappingSnapshot());
        if (mapping == null) {
            return Optional.empty();
        }
        return Optional.of(new EndpointVersion(
                objectTypeId,
                version.getId(),
                mapping,
                version.getMappingSnapshot(),
                version.getMappingRevision(),
                version.getSourceDatasetKind(),
                version.getSourceDatasetId(),
                version.getSourceDatasetVersionId(),
                version.getSourceSnapshotId()
        ));
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
