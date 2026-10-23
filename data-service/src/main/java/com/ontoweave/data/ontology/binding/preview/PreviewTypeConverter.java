package com.ontoweave.data.ontology.binding.preview;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class PreviewTypeConverter {

    private static final BigInteger INT32_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT32_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private PreviewTypeConverter() {
    }

    static Object convert(Object value, String datatype, List<String> enumValues) {
        if (value == null) {
            return null;
        }
        return switch (normalize(datatype)) {
            case "text" -> text(value);
            case "integer" -> integer(value);
            case "decimal" -> decimal(value);
            case "boolean" -> bool(value);
            case "date" -> date(value);
            case "timestamp", "timestamptz" -> timestamp(value);
            case "enum" -> enumValue(value, enumValues);
            default -> throw new IllegalArgumentException("unsupported datatype: " + datatype);
        };
    }

    static boolean isBlank(Object value) {
        return value == null || (value instanceof CharSequence text && text.toString().isBlank());
    }

    static Integer scaleOf(Object value) {
        return value instanceof BigDecimal decimal ? decimal.scale() : 0;
    }

    static String textValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Object text(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return value.toString();
        }
        throw new IllegalArgumentException("text requires a string or uuid");
    }

    private static Object integer(Object value) {
        BigInteger number;
        try {
            number = new BigDecimal(value.toString().trim()).toBigIntegerExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("integer requires an exact integer", exception);
        }
        if (number.compareTo(INT32_MIN) < 0 || number.compareTo(INT32_MAX) > 0) {
            throw new IllegalArgumentException("integer exceeds int32 range");
        }
        return number.longValue();
    }

    private static Object decimal(Object value) {
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("decimal requires a number", exception);
        }
    }

    private static Object bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "false".equals(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("boolean requires true or false");
    }

    private static Object date(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof CharSequence text) {
            return LocalDate.parse(text.toString().trim());
        }
        throw new IllegalArgumentException("date requires a LocalDate");
    }

    private static Object timestamp(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof ZonedDateTime timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime timestamp) {
            return timestamp.toInstant(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof CharSequence text) {
            return parseTimestamp(text.toString().trim());
        }
        throw new IllegalArgumentException("timestamp requires an ISO-8601 value");
    }

    private static Instant parseTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (RuntimeException ignoredAgain) {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            }
        }
    }

    private static Object enumValue(Object value, List<String> enumValues) {
        String text = value.toString();
        if (enumValues != null && !enumValues.contains(text)) {
            throw new IllegalArgumentException("enum value is not allowed: " + text);
        }
        return text;
    }

    private static String normalize(String type) {
        if (type == null) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        int parenthesis = normalized.indexOf('(');
        if (parenthesis >= 0) {
            normalized = normalized.substring(0, parenthesis).trim();
        }
        return switch (normalized) {
            case "timestamp with time zone", "timestamp_tz", "timestamp with zone" ->
                    "timestamptz";
            case "timestamp without time zone", "timestamp_ntz", "timestamp without zone" ->
                    "timestamp";
            default -> normalized;
        };
    }
}
