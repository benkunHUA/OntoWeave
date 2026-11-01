package com.ontoweave.data.ontology.edit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.edit.store.JdbcObjectEditStore;
import com.ontoweave.data.ontology.query.common.MappingSupport;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class EditContextResolver {

    private final JdbcObjectEditStore objectStore;
    private final ObjectMapper mapper;

    public EditContextResolver(JdbcObjectEditStore objectStore, ObjectMapper mapper) {
        this.objectStore = objectStore;
        this.mapper = mapper;
    }

    public Optional<EditContext> find(UUID ownerId, UUID objectTypeId) {
        return objectStore.findBinding(ownerId, objectTypeId)
                .filter(binding -> binding.versionId() != null)
                .map(binding -> new EditContext(
                        binding,
                        MappingSupport.parse(mapper, binding.mappingSnapshot())
                ));
    }

    public EditContext require(UUID ownerId, UUID objectTypeId) {
        return find(ownerId, objectTypeId)
                .orElseThrow(() -> EditSetErrors.conflict("NOT_INDEXED", objectTypeId, ""));
    }

    public record EditContext(
            JdbcObjectEditStore.ObjectBindingContext binding,
            MappingSnapshot mapping
    ) {

        public UUID versionId() {
            return binding.versionId();
        }
    }
}
