package com.ontoweave.data.ontology.query.common;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.query.api.dto.SortCondition;
import com.ontoweave.data.ontology.query.filter.QueryDatatype;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class SortParser {

    public List<SortSpec> parse(MappingSnapshot mapping, List<SortCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        if (conditions.size() > 3) {
            throw QueryErrors.invalidRequest("sort 最多支持 3 个字段");
        }
        List<SortSpec> specs = new ArrayList<>();
        for (SortCondition condition : conditions) {
            specs.add(parseOne(mapping, condition));
        }
        return List.copyOf(specs);
    }

    private SortSpec parseOne(MappingSnapshot mapping, SortCondition condition) {
        if (condition == null) {
            throw QueryErrors.invalidRequest("sort 条件不能为空");
        }
        UUID attributeId = uuid(condition.attributeId());
        MappingSnapshot.AttributeMapping attribute = mapping.attributes().stream()
                .filter(item -> attributeId.equals(item.attributeId()))
                .findFirst()
                .orElseThrow(() -> QueryErrors.invalidMapping(
                        "排序属性不在当前映射中",
                        Map.of("attributeId", attributeId.toString())
                ));
        if (attribute.isArray()) {
            throw QueryErrors.invalidMapping(
                    "数组属性不支持排序",
                    Map.of("attributeId", attributeId.toString())
            );
        }
        QueryDatatype datatype;
        try {
            datatype = QueryDatatype.from(attribute.datatype());
        } catch (IllegalArgumentException exception) {
            throw QueryErrors.invalidMapping(
                    "排序属性的 datatype 不受支持",
                    Map.of("attributeId", attributeId.toString(), "datatype", attribute.datatype())
            );
        }
        return new SortSpec(attributeId, datatype, descending(condition.direction()));
    }

    private boolean descending(String value) {
        String normalized = value == null ? "asc" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "asc" -> false;
            case "desc" -> true;
            default -> throw QueryErrors.invalidRequest("sort.direction 只能是 asc 或 desc");
        };
    }

    private UUID uuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim());
        } catch (IllegalArgumentException exception) {
            throw QueryErrors.invalidRequest("sort.attributeId 不是合法 UUID");
        }
    }
}
