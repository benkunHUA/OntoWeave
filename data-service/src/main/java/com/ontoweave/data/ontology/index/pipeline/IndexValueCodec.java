package com.ontoweave.data.ontology.index.pipeline;

import com.ontoweave.data.ontology.common.PrimaryKeyCodec;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexFailureCode;

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

public final class IndexValueCodec {

    private IndexValueCodec() {
    }

    public static String canonicalKey(Object value, String datatype) {
        if (value == null) {
            return null;
        }
        String type = normalize(datatype);
        if ("decimal".equals(type)) {
            return PrimaryKeyCodec.canonicalize(value, type, decimalScale(value));
        }
        return PrimaryKeyCodec.canonicalize(value, type, null);
    }

    public static Object convert(Object value, String datatype, List<String> enumValues) {
        if (value == null) {
            return null;
        }
        String type = normalize(datatype);
        try {
            return switch (type) {
                case "text" -> text(value);
                case "integer" -> integer(value);
                case "decimal" -> decimal(value);
                case "boolean" -> bool(value);
                case "date" -> date(value);
                case "timestamp", "timestamptz" -> timestamp(value);
                case "enum" -> enumValue(value, enumValues);
                default -> throw new IndexBuildException(
                        IndexFailureCode.TYPE_CONVERSION_FAILED,
                        "unsupported datatype: " + datatype
                );
            };
        } catch (IndexBuildException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IndexBuildException(
                    IndexFailureCode.TYPE_CONVERSION_FAILED,
                    "value does not match datatype " + datatype,
                    java.util.Map.of(),
                    exception
            );
        }
    }

    public static String title(Object value) {
        return value == null ? null : value.toString();
    }

    public static boolean isText(String datatype) {
        String type = normalize(datatype);
        return "text".equals(type) || "enum".equals(type);
    }

    public static boolean isNumeric(String datatype) {
        String type = normalize(datatype);
        return "integer".equals(type) || "decimal".equals(type);
    }

    public static boolean isDate(String datatype) {
        return "date".equals(normalize(datatype));
    }

    public static boolean isTimestamp(String datatype) {
        String type = normalize(datatype);
        return "timestamp".equals(type) || "timestamptz".equals(type);
    }

    private static String text(Object value) {
        if (value instanceof CharSequence || value instanceof Character) {
            return value.toString();
        }
        if (value instanceof java.util.UUID) {
            return value.toString();
        }
        throw new IllegalArgumentException("text value required");
    }

    private static BigDecimal integer(Object value) {
        BigInteger number = new BigDecimal(value.toString().trim()).toBigIntegerExact();
        if (number.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || number.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("integer value exceeds int32 range");
        }
        return new BigDecimal(number);
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString().trim());
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "false".equals(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("boolean value required");
    }

    private static LocalDate date(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString().trim());
    }

    private static Instant timestamp(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof ZonedDateTime zoned) {
            return zoned.toInstant();
        }
        if (value instanceof LocalDateTime local) {
            return local.toInstant(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(value.toString().trim());
    }

    private static String enumValue(Object value, List<String> candidates) {
        String text = text(value);
        if (candidates == null || !candidates.contains(text)) {
            throw new IndexBuildException(
                    IndexFailureCode.ENUM_OUT_OF_RANGE,
                    "enum value is not in the frozen candidates"
            );
        }
        return text;
    }

    private static Integer decimalScale(Object value) {
        try {
            BigDecimal decimal = value instanceof BigDecimal direct
                    ? direct
                    : new BigDecimal(value.toString().trim());
            return Math.max(0, decimal.scale());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String normalize(String datatype) {
        if (datatype == null) {
            return "";
        }
        String value = datatype.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "timestamp with time zone", "timestamp_tz", "timestamp with zone" ->
                    "timestamptz";
            case "timestamp without time zone", "timestamp_ntz", "timestamp without zone" ->
                    "timestamp";
            default -> value;
        };
    }
}
