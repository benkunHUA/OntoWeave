package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.connection.repository.DataConnectionRepository;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PublishedInputSchemaResolver implements InputSchemaResolver {

    private static final String DATASET_KIND_SYNC = "sync";
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
        if (ownerId == null || datasetId == null || !DATASET_KIND_SYNC.equals(datasetKind)) {
            return Optional.empty();
        }
        DatasetEntity dataset = datasets.findById(datasetId).orElse(null);
        if (dataset == null || dataset.getCurrentVersionId() == null) {
            return Optional.empty();
        }
        if (connections.findByIdAndOwnerId(dataset.getConnectionId(), ownerId).isEmpty()) {
            return Optional.empty();
        }
        DatasetVersionEntity version = versions.findByIdAndDatasetId(
                dataset.getCurrentVersionId(),
                datasetId
        ).orElse(null);
        if (version == null || version.getPublishedAt() == null) {
            return Optional.empty();
        }
        if (version.getSchemaJson() == null) {
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
