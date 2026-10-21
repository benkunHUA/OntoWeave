package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.table.api.ApiExpression;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.types.DataType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

final class FlinkLiteralCompiler {

    ApiExpression literal(JsonNode expression) {
        String dataType = expression.path("dataType").asText(null);
        JsonNode value = expression.path("value");
        return literal(value, dataType);
    }

    ApiExpression literal(JsonNode value, String dataType) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Expressions.nullOf(dataType(dataType));
        }
        String normalized = normalize(dataType);
        if (normalized.isEmpty()) {
            normalized = inferredType(value);
        }
        return switch (normalized) {
            case "boolean", "bool" -> Expressions.lit(
                    value.isBoolean() ? value.asBoolean() : Boolean.parseBoolean(value.asText()),
                    DataTypes.BOOLEAN().notNull()
            );
            case "tinyint", "byte" -> Expressions.lit(
                    (byte) integral(value, Byte.MIN_VALUE, Byte.MAX_VALUE, "tinyint"),
                    DataTypes.TINYINT().notNull()
            );
            case "smallint", "short" -> Expressions.lit(
                    (short) integral(value, Short.MIN_VALUE, Short.MAX_VALUE, "smallint"),
                    DataTypes.SMALLINT().notNull()
            );
            case "int", "integer" -> Expressions.lit(
                    integral(value, Integer.MIN_VALUE, Integer.MAX_VALUE, "int"),
                    DataTypes.INT().notNull()
            );
            case "bigint", "long" -> Expressions.lit(
                    longIntegral(value), DataTypes.BIGINT().notNull()
            );
            case "float", "real" -> Expressions.lit(
                    (float) floating(value, "float"), DataTypes.FLOAT().notNull()
            );
            case "double" -> Expressions.lit(
                    floating(value, "double"), DataTypes.DOUBLE().notNull()
            );
            case "decimal" -> Expressions.lit(
                    decimal(value), DataTypes.DECIMAL(38, 18).notNull()
            );
            case "date" -> Expressions.lit(
                    date(value), DataTypes.DATE().notNull()
            );
            case "timestamp", "datetime" -> Expressions.lit(
                    timestamp(value), DataTypes.TIMESTAMP(3).notNull()
            );
            case "binary", "bytes", "varbinary" -> Expressions.lit(
                    java.util.Base64.getDecoder().decode(text(value)),
                    DataTypes.BYTES().notNull()
            );
            default -> Expressions.lit(text(value), DataTypes.STRING().notNull());
        };
    }

    DataType dataType(String value) {
        String normalized = normalize(value);
        if (normalized.startsWith("decimal")) {
            return DataTypes.DECIMAL(38, 18);
        }
        return switch (normalized) {
            case "boolean", "bool" -> DataTypes.BOOLEAN();
            case "tinyint", "byte" -> DataTypes.TINYINT();
            case "smallint", "short" -> DataTypes.SMALLINT();
            case "int", "integer" -> DataTypes.INT();
            case "bigint", "long" -> DataTypes.BIGINT();
            case "float", "real" -> DataTypes.FLOAT();
            case "double" -> DataTypes.DOUBLE();
            case "date" -> DataTypes.DATE();
            case "timestamp", "datetime" -> DataTypes.TIMESTAMP(3);
            case "binary", "bytes", "varbinary" -> DataTypes.BYTES();
            default -> DataTypes.STRING();
        };
    }

    private String inferredType(JsonNode value) {
        if (value.isBoolean()) {
            return "boolean";
        }
        if (value.isIntegralNumber()) {
            return "bigint";
        }
        if (value.isFloatingPointNumber()) {
            return "double";
        }
        return "string";
    }

    private int integral(JsonNode value, int minimum, int maximum, String type) {
        long number = longIntegral(value);
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException("Literal value is out of range for " + type);
        }
        return (int) number;
    }

    private long longIntegral(JsonNode value) {
        if (value.isIntegralNumber() && value.canConvertToLong()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Literal value is not an integer", error);
        }
    }

    private double floating(JsonNode value, String type) {
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(text(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Literal value is not a " + type, error);
        }
    }

    private BigDecimal decimal(JsonNode value) {
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(text(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Literal value is not a decimal", error);
        }
    }

    private LocalDate date(JsonNode value) {
        try {
            return LocalDate.parse(text(value));
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Literal value is not a valid date", error);
        }
    }

    private LocalDateTime timestamp(JsonNode value) {
        String text = text(value);
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException first) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException second) {
                throw new IllegalArgumentException("Literal value is not a valid timestamp", second);
            }
        }
    }

    private String text(JsonNode value) {
        if (value.isValueNode()) {
            return value.asText();
        }
        throw new IllegalArgumentException("Literal value must be a scalar");
    }

    String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
