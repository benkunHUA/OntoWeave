package com.ontoweave.data.ontology.edit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.query.common.MappingSupport;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import com.ontoweave.data.ontology.query.filter.QueryDatatype;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ObjectEditValues {

    private final ObjectMapper mapper;

    public ObjectEditValues(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode createProperties(
            MappingSnapshot mapping,
            String primaryKey,
            Map<UUID, JsonNode> values
    ) {
        ObjectNode node = properties(values);
        UUID primaryKeyAttributeId = mapping.primaryKeyAttributeId();
        if (primaryKeyAttributeId != null && node.get(primaryKeyAttributeId.toString()) == null) {
            node.set(primaryKeyAttributeId.toString(), primaryValueNode(mapping, primaryKey));
        }
        UUID titleAttributeId = mapping.titleKeyAttributeId();
        if (titleAttributeId != null && node.get(titleAttributeId.toString()) == null) {
            node.set(titleAttributeId.toString(), mapper.getNodeFactory().textNode(primaryKey));
        }
        return node;
    }

    public ObjectNode patch(
            ObjectNode existing,
            Map<UUID, JsonNode> set,
            Collection<UUID> clear
    ) {
        ObjectNode patch = existing == null
                ? mapper.createObjectNode()
                : existing.deepCopy();
        set.forEach((attributeId, value) -> {
            if (value == null || value.isNull()) {
                patch.remove(attributeId.toString());
            } else {
                patch.set(attributeId.toString(), value);
            }
        });
        clear.forEach(attributeId -> patch.remove(attributeId.toString()));
        return patch;
    }

    public Set<UUID> cleared(String jsonArray) {
        Set<UUID> result = new HashSet<>();
        if (jsonArray == null || jsonArray.isBlank()) {
            return result;
        }
        try {
            JsonNode node = mapper.readTree(jsonArray);
            if (!node.isArray()) {
                return result;
            }
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    result.add(UUID.fromString(item.asText()));
                }
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("invalid cleared properties", exception);
        }
    }

    public String titleValue(MappingSnapshot mapping, ObjectNode patch) {
        UUID titleId = mapping.titleKeyAttributeId();
        if (titleId == null) {
            return null;
        }
        JsonNode value = patch.get(titleId.toString());
        return value == null || value.isNull() || value.asText().isBlank()
                ? null : value.asText();
    }

    public String effectiveTitle(
            MappingSnapshot mapping,
            String primaryKey,
            boolean titleEdited,
            String titleValue,
            String fallbackTitle
    ) {
        if (!titleEdited) {
            return fallbackTitle == null || fallbackTitle.isBlank() ? primaryKey : fallbackTitle;
        }
        return titleValue == null || titleValue.isBlank() ? primaryKey : titleValue;
    }

    public void validateValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isValueNode()) {
            throw QueryErrors.invalidRequest("属性值必须是标量");
        }
    }

    public MappingSnapshot.AttributeMapping requireAttribute(
            MappingSnapshot mapping,
            UUID attributeId
    ) {
        MappingSnapshot.AttributeMapping attribute = MappingSupport.attribute(mapping, attributeId);
        if (attribute == null) {
            throw QueryErrors.invalidMapping("属性不在当前映射中",
                    Map.of("attributeId", String.valueOf(attributeId)));
        }
        if (attribute.isArray()) {
            throw QueryErrors.invalidMapping("v1 不支持修改数组属性",
                    Map.of("attributeId", attributeId.toString()));
        }
        return attribute;
    }

    public String datatype(MappingSnapshot mapping, UUID attributeId) {
        return requireAttribute(mapping, attributeId).datatype();
    }

    public BigDecimal primaryNumber(MappingSnapshot mapping, String primaryKey) {
        QueryDatatype type = MappingSupport.primaryKeyDatatype(mapping);
        return type == QueryDatatype.INTEGER || type == QueryDatatype.DECIMAL
                ? new BigDecimal(primaryKey) : null;
    }

    public LocalDate primaryDate(MappingSnapshot mapping, String primaryKey) {
        return MappingSupport.primaryKeyDatatype(mapping) == QueryDatatype.DATE
                ? LocalDate.parse(primaryKey) : null;
    }

    public Timestamp primaryTimestamp(MappingSnapshot mapping, String primaryKey) {
        return MappingSupport.primaryKeyDatatype(mapping) == QueryDatatype.TIMESTAMP
                ? Timestamp.from(Instant.parse(primaryKey)) : null;
    }

    private ObjectNode properties(Map<UUID, JsonNode> values) {
        ObjectNode node = mapper.createObjectNode();
        values.forEach((attributeId, value) -> {
            if (value != null && !value.isNull()) {
                node.set(attributeId.toString(), value);
            }
        });
        return node;
    }

    private JsonNode primaryValueNode(MappingSnapshot mapping, String primaryKey) {
        return switch (MappingSupport.primaryKeyDatatype(mapping)) {
            case INTEGER -> mapper.getNodeFactory().numberNode(Long.parseLong(primaryKey));
            case DECIMAL -> mapper.getNodeFactory().numberNode(new BigDecimal(primaryKey));
            case BOOLEAN -> mapper.getNodeFactory().booleanNode(Boolean.parseBoolean(primaryKey));
            default -> mapper.getNodeFactory().textNode(primaryKey);
        };
    }
}
