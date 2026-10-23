package com.ontoweave.data.ontology.binding.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import org.springframework.stereotype.Component;

@Component
public class BindingJson {

    private final ObjectMapper mapper;

    public BindingJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public MappingSnapshot mapping(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return mapper.treeToValue(node, MappingSnapshot.class);
        } catch (Exception exception) {
            throw new IllegalStateException("stored mapping is invalid", exception);
        }
    }

    public JsonNode node(MappingSnapshot mapping) {
        return mapping == null ? mapper.nullNode() : mapper.valueToTree(mapping);
    }

    public MappingValidationResult validation(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return mapper.treeToValue(node, MappingValidationResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("stored validation result is invalid", exception);
        }
    }

    public JsonNode node(MappingValidationResult validation) {
        return validation == null ? mapper.nullNode() : mapper.valueToTree(validation);
    }
}
