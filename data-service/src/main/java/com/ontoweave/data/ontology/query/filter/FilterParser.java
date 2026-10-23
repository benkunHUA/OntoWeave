package com.ontoweave.data.ontology.query.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FilterParser {

    public ParsedFilters parse(MappingSnapshot mapping, List<FilterCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return ParsedFilters.empty();
        }
        if (mapping == null) {
            throw QueryErrors.invalidMapping("当前索引版本缺少映射快照", Map.of());
        }
        Map<UUID, MappingSnapshot.AttributeMapping> attributes = attributesById(mapping);
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        for (FilterCondition condition : conditions) {
            parseOne(condition, attributes, clauses, parameters);
        }
        return new ParsedFilters(clauses, parameters);
    }

    private void parseOne(
            FilterCondition condition,
            Map<UUID, MappingSnapshot.AttributeMapping> attributes,
            List<String> clauses,
            List<Object> parameters
    ) {
        if (condition == null) {
            throw invalidFilter("筛选条件不能为空", Map.of());
        }
        UUID attributeId = parseAttributeId(condition.attributeId());
        MappingSnapshot.AttributeMapping attribute = attributes.get(attributeId);
        if (attribute == null) {
            throw invalidFilter(
                    "筛选属性不在当前映射中",
                    Map.of("attributeId", attributeId.toString())
            );
        }
        if (attribute.isArray()) {
            throw invalidFilter(
                    "数组属性不支持筛选",
                    Map.of("attributeId", attributeId.toString())
            );
        }
        FilterOperator operator = FilterOperator.from(condition.op()).orElseThrow(() ->
                invalidFilter(
                        "不支持的筛选操作符",
                        Map.of("attributeId", attributeId.toString(), "op", String.valueOf(condition.op()))
                )
        );
        QueryDatatype datatype = datatype(attributeId, attribute.datatype());
        if (!datatype.supports(operator)) {
            throw invalidFilter(
                    "筛选操作符与属性类型不匹配",
                    Map.of(
                            "attributeId", attributeId.toString(),
                            "op", operator.wireName(),
                            "datatype", attribute.datatype()
                    )
            );
        }
        String column = datatype.valueColumn();
        if (operator == FilterOperator.IS_NULL) {
            clauses.add(notExistsNonNull(column));
            parameters.add(attributeId);
            return;
        }
        if (operator == FilterOperator.IS_NOT_NULL) {
            clauses.add(existsNonNull(column));
            parameters.add(attributeId);
            return;
        }
        Object value = typedValue(datatype, operator, condition.value(), attributeId);
        clauses.add(comparison(column, operator));
        parameters.add(attributeId);
        parameters.add(value);
    }

    private UUID parseAttributeId(String value) {
        if (value == null || value.isBlank()) {
            throw invalidFilter("筛选条件缺少 attributeId", Map.of());
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw invalidFilter("attributeId 不是合法 UUID", Map.of("attributeId", value));
        }
    }

    private QueryDatatype datatype(UUID attributeId, String datatype) {
        try {
            return QueryDatatype.from(datatype);
        } catch (IllegalArgumentException exception) {
            throw invalidFilter(
                    "筛选属性的 datatype 不受支持",
                    Map.of("attributeId", attributeId.toString(), "datatype", String.valueOf(datatype))
            );
        }
    }

    private Object typedValue(
            QueryDatatype datatype,
            FilterOperator operator,
            JsonNode node,
            UUID attributeId
    ) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw invalidFilter(
                    "筛选条件缺少 value",
                    Map.of("attributeId", attributeId.toString(), "op", operator.wireName())
            );
        }
        try {
            return switch (datatype) {
                case TEXT, ENUM -> textValue(datatype, operator, node, attributeId);
                case INTEGER -> integerValue(node, attributeId);
                case DECIMAL -> decimalValue(node, attributeId);
                case BOOLEAN -> booleanValue(node, attributeId);
                case DATE -> dateValue(node, attributeId);
                case TIMESTAMP -> timestampValue(node, attributeId);
            };
        } catch (IllegalArgumentException exception) {
            if (exception instanceof FilterValueException filterValueException) {
                throw invalidFilter(
                        filterValueException.getMessage(),
                        Map.of(
                                "attributeId", attributeId.toString(),
                                "op", operator.wireName(),
                                "datatype", datatype.name().toLowerCase(java.util.Locale.ROOT)
                        )
                );
            }
            throw invalidFilter(
                    "筛选值与属性类型不匹配",
                    Map.of("attributeId", attributeId.toString(), "op", operator.wireName())
            );
        }
    }

    private String textValue(
            QueryDatatype datatype,
            FilterOperator operator,
            JsonNode node,
            UUID attributeId
    ) {
        if (!node.isTextual()) {
            throw new FilterValueException("筛选值必须是字符串");
        }
        String value = node.textValue();
        if (operator == FilterOperator.CONTAINS) {
            if (!datatype.isTextual()) {
                throw new FilterValueException("contains 只支持 text 或 enum");
            }
            return "%" + value + "%";
        }
        return value;
    }

    private BigDecimal integerValue(JsonNode node, UUID attributeId) {
        if (!node.isNumber()) {
            throw new FilterValueException("筛选值必须是整数");
        }
        BigDecimal value = node.decimalValue();
        try {
            value.toBigIntegerExact();
            return value;
        } catch (ArithmeticException exception) {
            throw new FilterValueException("筛选值必须是整数");
        }
    }

    private BigDecimal decimalValue(JsonNode node, UUID attributeId) {
        if (!node.isNumber()) {
            throw new FilterValueException("筛选值必须是数字");
        }
        return node.decimalValue();
    }

    private Boolean booleanValue(JsonNode node, UUID attributeId) {
        if (!node.isBoolean()) {
            throw new FilterValueException("筛选值必须是布尔值");
        }
        return node.booleanValue();
    }

    private LocalDate dateValue(JsonNode node, UUID attributeId) {
        if (!node.isTextual()) {
            throw new FilterValueException("date 筛选值必须是 yyyy-MM-dd 字符串");
        }
        try {
            return LocalDate.parse(node.textValue().trim());
        } catch (DateTimeParseException exception) {
            throw new FilterValueException("date 筛选值必须是 yyyy-MM-dd 字符串");
        }
    }

    private Instant timestampValue(JsonNode node, UUID attributeId) {
        if (!node.isTextual()) {
            throw new FilterValueException("timestamp 筛选值必须是 ISO-8601 字符串");
        }
        String value = node.textValue().trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try offset and local timestamp forms below.
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // Try a local timestamp interpreted as UTC.
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new FilterValueException("timestamp 筛选值必须是 ISO-8601 字符串");
        }
    }

    private Map<UUID, MappingSnapshot.AttributeMapping> attributesById(MappingSnapshot mapping) {
        Map<UUID, MappingSnapshot.AttributeMapping> attributes = new HashMap<>();
        for (MappingSnapshot.AttributeMapping attribute : mapping.attributes()) {
            if (attribute.attributeId() != null) {
                attributes.putIfAbsent(attribute.attributeId(), attribute);
            }
        }
        return attributes;
    }

    private String notExistsNonNull(String column) {
        return """
                NOT EXISTS (SELECT 1 FROM data.ontology_object_value v
                            WHERE v.version_id = o.version_id
                              AND v.primary_key_value = o.primary_key_value
                              AND v.attribute_id = ?
                              AND v.%s IS NOT NULL)
                """.formatted(column);
    }

    private String existsNonNull(String column) {
        return """
                EXISTS (SELECT 1 FROM data.ontology_object_value v
                        WHERE v.version_id = o.version_id
                          AND v.primary_key_value = o.primary_key_value
                          AND v.attribute_id = ?
                          AND v.%s IS NOT NULL)
                """.formatted(column);
    }

    private String comparison(String column, FilterOperator operator) {
        String sqlOperator = switch (operator) {
            case EQ -> "=";
            case NE -> "<>";
            case CONTAINS -> "ILIKE";
            case GT -> ">";
            case GTE -> ">=";
            case LT -> "<";
            case LTE -> "<=";
            case IS_NULL, IS_NOT_NULL -> throw new IllegalArgumentException("unary operator");
        };
        return """
                EXISTS (SELECT 1 FROM data.ontology_object_value v
                        WHERE v.version_id = o.version_id
                          AND v.primary_key_value = o.primary_key_value
                          AND v.attribute_id = ?
                          AND v.%s %s ?)
                """.formatted(column, sqlOperator);
    }

    private com.ontoweave.data.common.web.ApiException invalidFilter(
            String message,
            Map<String, Object> details
    ) {
        return QueryErrors.invalidMapping(message, details);
    }

    private static final class FilterValueException extends IllegalArgumentException {

        private FilterValueException(String message) {
            super(message);
        }
    }
}
