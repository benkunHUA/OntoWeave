package com.ontoweave.data.ontology.query.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.query.filter.QueryDatatype;

import java.util.Map;
import java.util.UUID;

public final class MappingSupport {

    private MappingSupport() {
    }

    public static MappingSnapshot parse(ObjectMapper mapper, JsonNode snapshot) {
        if (snapshot == null || snapshot.isNull() || snapshot.isMissingNode()) {
            throw QueryErrors.invalidMapping("当前索引版本缺少映射快照", Map.of());
        }
        try {
            return mapper.treeToValue(snapshot, MappingSnapshot.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw QueryErrors.invalidMapping("当前索引版本的映射快照不合法", Map.of());
        }
    }

    public static QueryDatatype primaryKeyDatatype(MappingSnapshot mapping) {
        MappingSnapshot.AttributeMapping primaryKey = attribute(
                mapping,
                mapping.primaryKeyAttributeId()
        );
        if (primaryKey == null) {
            throw QueryErrors.invalidMapping(
                    "主键属性不在冻结映射中",
                    Map.of("primaryKeyAttributeId", String.valueOf(mapping.primaryKeyAttributeId()))
            );
        }
        try {
            return QueryDatatype.from(primaryKey.datatype());
        } catch (IllegalArgumentException exception) {
            throw QueryErrors.invalidMapping(
                    "主键属性的 datatype 不受支持",
                    Map.of(
                            "primaryKeyAttributeId", primaryKey.attributeId().toString(),
                            "datatype", String.valueOf(primaryKey.datatype())
                    )
            );
        }
    }

    public static MappingSnapshot.AttributeMapping attribute(
            MappingSnapshot mapping,
            UUID attributeId
    ) {
        if (mapping == null || attributeId == null) {
            return null;
        }
        return mapping.attributes().stream()
                .filter(attribute -> attributeId.equals(attribute.attributeId()))
                .findFirst()
                .orElse(null);
    }

    public static JsonNode propertyValue(
            JsonNode properties,
            MappingSnapshot.AttributeMapping attribute
    ) {
        if (properties == null || properties.isNull() || attribute == null) {
            return NullNode.getInstance();
        }
        if (properties.isObject()) {
            for (String key : propertyKeys(attribute)) {
                JsonNode value = properties.get(key);
                if (value != null) {
                    return value;
                }
            }
            return NullNode.getInstance();
        }
        if (properties.isArray()) {
            for (JsonNode entry : properties) {
                if (!entry.isObject()) {
                    continue;
                }
                String entryId = firstText(entry, "attributeId", "attribute_id");
                if (propertyKeys(attribute).contains(entryId)) {
                    JsonNode value = entry.get("value");
                    return value == null ? NullNode.getInstance() : value;
                }
            }
        }
        return NullNode.getInstance();
    }

    private static java.util.List<String> propertyKeys(MappingSnapshot.AttributeMapping attribute) {
        return java.util.stream.Stream.of(
                        attribute.attributeId() == null ? null : attribute.attributeId().toString(),
                        attribute.column()
                )
                .filter(key -> key != null && !key.isBlank())
                .toList();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }
}
