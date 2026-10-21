package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.table.api.ApiExpression;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Expressions;
import org.apache.flink.table.types.DataType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FlinkLiteralCompiler {

    private static final Pattern DECIMAL = Pattern.compile(
            "^decimal\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)$"
    );
    private static final Pattern TIMESTAMP = Pattern.compile("^timestamp\\s*\\(\\s*(\\d+)\\s*\\)$");
    private static final Pattern TIME = Pattern.compile("^time\\s*\\(\\s*(\\d+)\\s*\\)$");
    private static final Pattern LENGTH = Pattern.compile(
            "^(varchar|char|binary|varbinary)\\s*\\(\\s*(\\d+)\\s*\\)$"
    );

    ApiExpression literal(JsonNode expression) {
        return literal(expression.path("value"), expression.path("dataType").asText(null));
    }

    ApiExpression literal(JsonNode value, String dataType) {
        String declared = normalize(dataType);
        if (declared.isEmpty() && value != null && !value.isNull()) {
            declared = inferredType(value);
        }
        return literal(value, parse(declared));
    }

    private ApiExpression literal(JsonNode value, ParsedType type) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Expressions.nullOf(type.dataType());
        }
        return switch (type.base()) {
            case "boolean" -> Expressions.lit(bool(value), type.dataType().notNull());
            case "tinyint" -> Expressions.lit(
                    (byte) integral(value, Byte.MIN_VALUE, Byte.MAX_VALUE, "tinyint"),
                    type.dataType().notNull()
            );
            case "smallint" -> Expressions.lit(
                    (short) integral(value, Short.MIN_VALUE, Short.MAX_VALUE, "smallint"),
                    type.dataType().notNull()
            );
            case "int" -> Expressions.lit(
                    integral(value, Integer.MIN_VALUE, Integer.MAX_VALUE, "int"),
                    type.dataType().notNull()
            );
            case "bigint" -> Expressions.lit(longIntegral(value), type.dataType().notNull());
            case "float" -> Expressions.lit((float) floating(value), type.dataType().notNull());
            case "double" -> Expressions.lit(floating(value), type.dataType().notNull());
            case "decimal" -> Expressions.lit(decimal(value), type.dataType().notNull());
            case "date" -> Expressions.lit(date(value), type.dataType().notNull());
            case "timestamp" -> Expressions.lit(timestamp(value), type.dataType().notNull());
            case "time" -> Expressions.lit(time(value), type.dataType().notNull());
            case "binary" -> Expressions.lit(binary(value), type.dataType().notNull());
            case "string" -> Expressions.lit(text(value), type.dataType().notNull());
            default -> throw new IllegalArgumentException("Unsupported literal data type: " + type.base());
        };
    }

    DataType dataType(String value) {
        return parse(value).dataType();
    }

    private ParsedType parse(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return new ParsedType("string", DataTypes.STRING());
        }
        Matcher decimal = DECIMAL.matcher(normalized);
        if (decimal.matches()) {
            return decimal(Integer.parseInt(decimal.group(1)), Integer.parseInt(decimal.group(2)));
        }
        Matcher timestamp = TIMESTAMP.matcher(normalized);
        if (timestamp.matches()) {
            return timestamp(Integer.parseInt(timestamp.group(1)));
        }
        Matcher time = TIME.matcher(normalized);
        if (time.matches()) {
            return time(Integer.parseInt(time.group(1)));
        }
        Matcher length = LENGTH.matcher(normalized);
        if (length.matches()) {
            return length(length.group(1), Integer.parseInt(length.group(2)));
        }
        return switch (normalized) {
            case "boolean", "bool" -> new ParsedType("boolean", DataTypes.BOOLEAN());
            case "tinyint", "byte" -> new ParsedType("tinyint", DataTypes.TINYINT());
            case "smallint", "short" -> new ParsedType("smallint", DataTypes.SMALLINT());
            case "int", "integer" -> new ParsedType("int", DataTypes.INT());
            case "bigint", "long" -> new ParsedType("bigint", DataTypes.BIGINT());
            case "float", "real" -> new ParsedType("float", DataTypes.FLOAT());
            case "double" -> new ParsedType("double", DataTypes.DOUBLE());
            case "decimal" -> decimal(38, 18);
            case "date" -> new ParsedType("date", DataTypes.DATE());
            case "timestamp", "datetime" -> timestamp(3);
            case "time" -> time(3);
            case "binary", "bytes", "varbinary" -> new ParsedType("binary", DataTypes.BYTES());
            case "string", "varchar", "char", "text" -> new ParsedType("string", DataTypes.STRING());
            default -> throw new IllegalArgumentException("Unsupported literal data type: " + value);
        };
    }

    private ParsedType decimal(int precision, int scale) {
        if (precision < 1 || precision > 38 || scale < 0 || scale > precision) {
            throw new IllegalArgumentException("Invalid decimal precision or scale");
        }
        return new ParsedType("decimal", DataTypes.DECIMAL(precision, scale));
    }

    private ParsedType timestamp(int precision) {
        if (precision < 0 || precision > 9) {
            throw new IllegalArgumentException("Invalid timestamp precision: " + precision);
        }
        return new ParsedType("timestamp", DataTypes.TIMESTAMP(precision));
    }

    private ParsedType time(int precision) {
        if (precision < 0 || precision > 9) {
            throw new IllegalArgumentException("Invalid time precision: " + precision);
        }
        return new ParsedType("time", DataTypes.TIME(precision));
    }

    private ParsedType length(String name, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Invalid " + name + " length: " + length);
        }
        return switch (name) {
            case "varchar" -> new ParsedType("string", DataTypes.VARCHAR(length));
            case "char" -> new ParsedType("string", DataTypes.CHAR(length));
            case "binary" -> new ParsedType("binary", DataTypes.BINARY(length));
            case "varbinary" -> new ParsedType("binary", DataTypes.VARBINARY(length));
            default -> throw new IllegalArgumentException("Unsupported parameterized type: " + name);
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

    private boolean bool(JsonNode value) {
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String text = text(value);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("Literal value is not a boolean");
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

    private double floating(JsonNode value) {
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(text(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Literal value is not a number", error);
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
        try {
            return LocalDateTime.parse(text(value).trim().replace(' ', 'T'));
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Literal value is not a valid timestamp", error);
        }
    }

    private LocalTime time(JsonNode value) {
        try {
            return LocalTime.parse(text(value).trim());
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Literal value is not a valid time", error);
        }
    }

    private byte[] binary(JsonNode value) {
        try {
            return Base64.getDecoder().decode(text(value));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Literal value is not valid Base64", error);
        }
    }

    private String text(JsonNode value) {
        if (value.isValueNode()) {
            return value.asText();
        }
        throw new IllegalArgumentException("Literal value must be a scalar");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ParsedType(String base, DataType dataType) {
    }

}
