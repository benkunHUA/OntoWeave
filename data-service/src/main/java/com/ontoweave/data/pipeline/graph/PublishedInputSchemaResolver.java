package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PublishedInputSchemaResolver implements InputSchemaResolver {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_REQUIRED = "required";

    private final DatasetRepository datasets;
    private final DatasetVersionRepository versions;
    private final DataConnectionRepository connections;

    public PublishedInputSchemaResolver(
            DatasetRepository datasets,
            DatasetVersionRepository versions,
            DataConnectionRepository connections
    ) {
        this.datasets = datasets;
        this.versions = versions;
        this.connections = connections;
    }

    @Override
    public Optional<InputSchema> resolve(UUID ownerId, String datasetKind, UUID datasetId) {
        if (ownerId == null || datasetId == null || !SYNC_DATASET_KIND.equals(datasetKind)) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolveAll(ownerId, Set.of(datasetId)).get(datasetId));
    }

    @Override
    public Map<UUID, InputSchema> resolveAll(UUID ownerId, Set<UUID> datasetIds) {
        if (ownerId == null || datasetIds == null || datasetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, DatasetEntity> datasetsById = new HashMap<>();
        for (DatasetEntity dataset : datasets.findAllById(datasetIds)) {
            if (dataset.getCurrentVersionId() != null) {
                datasetsById.put(dataset.getId(), dataset);
            }
        }
        if (datasetsById.isEmpty()) {
            return Map.of();
        }
        Set<UUID> connectionIds = datasetsById.values().stream()
                .map(DatasetEntity::getConnectionId)
                .collect(Collectors.toSet());
        Set<UUID> visibleConnections = connections.findByIdInAndOwnerId(connectionIds, ownerId).stream()
                .map(connection -> connection.getId())
                .collect(Collectors.toSet());
        Set<UUID> versionIds = datasetsById.values().stream()
                .filter(dataset -> visibleConnections.contains(dataset.getConnectionId()))
                .map(DatasetEntity::getCurrentVersionId)
                .collect(Collectors.toSet());
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, DatasetVersionEntity> versionsById = new HashMap<>();
        for (DatasetVersionEntity version : versions.findAllById(versionIds)) {
            versionsById.put(version.getId(), version);
        }
        Map<UUID, InputSchema> resolved = new LinkedHashMap<>();
        for (DatasetEntity dataset : datasetsById.values()) {
            if (!visibleConnections.contains(dataset.getConnectionId())) {
                continue;
            }
            DatasetVersionEntity version = versionsById.get(dataset.getCurrentVersionId());
            schema(dataset, version).ifPresent(input -> resolved.put(dataset.getId(), input));
        }
        return resolved;
    }

    private Optional<InputSchema> schema(DatasetEntity dataset, DatasetVersionEntity version) {
        if (version == null || !dataset.getId().equals(version.getDatasetId())
                || version.getPublishedAt() == null || version.getSchemaJson() == null) {
            return Optional.empty();
        }
        JsonNode fieldsNode = version.getSchemaJson().path("fields");
        if (!fieldsNode.isArray()) {
            return Optional.empty();
        }
        List<FieldSchema> fields = new ArrayList<>();
        for (JsonNode field : fieldsNode) {
            String name = field.path(FIELD_NAME).asText(null);
            String type = field.path(FIELD_TYPE).asText(null);
            if (name == null || name.isBlank() || type == null || type.isBlank()) {
                continue;
            }
            fields.add(new FieldSchema(name, type, !field.path(FIELD_REQUIRED).asBoolean(false)));
        }
        return Optional.of(new InputSchema(version.getId(), fields));
    }
}
