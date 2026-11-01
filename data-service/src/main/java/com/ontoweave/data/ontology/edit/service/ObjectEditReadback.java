package com.ontoweave.data.ontology.edit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.edit.model.EditSetPreview;
import com.ontoweave.data.ontology.edit.model.EditSetResult;
import com.ontoweave.data.ontology.edit.store.JdbcObjectEditStore;
import com.ontoweave.data.ontology.query.repository.JdbcObjectQuery;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ObjectEditReadback {

    private final JdbcObjectEditStore objectStore;
    private final JdbcObjectQuery objectQuery;
    private final EditContextResolver contexts;
    private final ObjectMapper mapper;

    public ObjectEditReadback(
            JdbcObjectEditStore objectStore,
            JdbcObjectQuery objectQuery,
            EditContextResolver contexts,
            ObjectMapper mapper
    ) {
        this.objectStore = objectStore;
        this.objectQuery = objectQuery;
        this.contexts = contexts;
        this.mapper = mapper;
    }

    EditSetResult.ObjectResult read(UUID ownerId, UUID objectTypeId, String primaryKey) {
        EditContextResolver.EditContext context = contexts.require(ownerId, objectTypeId);
        var row = objectQuery.findDetail(context.versionId(), ownerId, objectTypeId, primaryKey);
        long version = currentVersion(ownerId, objectTypeId, primaryKey);
        if (row.isEmpty()) {
            return new EditSetResult.ObjectResult(
                    objectTypeId, primaryKey, false, null, mapper.createObjectNode(), version
            );
        }
        return new EditSetResult.ObjectResult(
                objectTypeId, primaryKey, true, row.get().title(), row.get().properties(), version
        );
    }

    long currentVersion(UUID ownerId, UUID objectTypeId, String primaryKey) {
        return objectStore.findEdit(ownerId, objectTypeId, primaryKey)
                .map(JdbcObjectEditStore.ObjectEditRow::version)
                .orElse(0L);
    }

    JsonNode state(String primaryKey, String title, JsonNode properties, long version) {
        return mapper.valueToTree(new EditSetPreview.ObjectState(
                primaryKey, title, properties, version
        ));
    }
}
