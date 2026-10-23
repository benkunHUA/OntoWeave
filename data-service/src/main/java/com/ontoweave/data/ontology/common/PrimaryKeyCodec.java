package com.ontoweave.data.ontology.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

public final class PrimaryKeyCodec {

    private static final BigInteger INT32_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT32_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private PrimaryKeyCodec() {
    }

    public static String canonicalize(Object value, String datatype, Integer scale) {
        if (value == null) {
            throw new IllegalArgumentException("primary key value is required");
        }
        String normalizedType = normalizeType(datatype);
        return switch (normalizedType) {
            case "text" -> text(value);
            case "integer" -> integer(value);
            case "decimal" -> decimal(value, scale);
            case "date" -> date(value);
            case "timestamp", "timestamptz" -> timestamp(value);
            default -> throw new IllegalArgumentException("unsupported primary key type: " + datatype);
        };
    }

    private static String text(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return value.toString();
        }
        throw new IllegalArgumentException("text primary key requires a string or uuid");
    }

    private static String integer(Object value) {
        BigInteger number;
        try {
            number = new BigDecimal(value.toString().trim()).toBigIntegerExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("integer primary key requires an integer", exception);
        }
        if (number.compareTo(INT32_MIN) < 0 || number.compareTo(INT32_MAX) > 0) {
            throw new IllegalArgumentException("integer primary key exceeds int32 range");
        }
        return number.toString();
    }

    private static String decimal(Object value, Integer scale) {
        if (scale == null) {
            throw new IllegalArgumentException("decimal scale is required");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("decimal scale must not be negative");
        }
        try {
            return new BigDecimal(value.toString().trim())
                    .setScale(scale, RoundingMode.UNNECESSARY)
                    .toPlainString();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "decimal primary key does not match scale " + scale,
                    exception
            );
        }
    }

    private static String date(Object value) {
        LocalDate date;
        if (value instanceof LocalDate localDate) {
            date = localDate;
        } else if (value instanceof java.sql.Date sqlDate) {
            date = sqlDate.toLocalDate();
        } else if (value instanceof CharSequence text) {
            try {
                date = LocalDate.parse(text.toString().trim());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("date primary key must use yyyy-MM-dd", exception);
            }
        } else {
            throw new IllegalArgumentException("unsupported date primary key value");
        }
        return date.toString();
    }

    private static String timestamp(Object value) {
        Instant instant;
        if (value instanceof Instant direct) {
            instant = direct;
        } else if (value instanceof OffsetDateTime offset) {
            instant = offset.toInstant();
        } else if (value instanceof ZonedDateTime zoned) {
            instant = zoned.toInstant();
        } else if (value instanceof LocalDateTime local) {
            instant = local.toInstant(ZoneOffset.UTC);
        } else if (value instanceof java.sql.Timestamp timestamp) {
            instant = timestamp.toInstant();
        } else if (value instanceof CharSequence text) {
            instant = parseTimestamp(text.toString().trim());
        } else {
            throw new IllegalArgumentException("unsupported timestamp primary key value");
        }
        return instant.toString();
    }

    private static Instant parseTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try an explicitly offset value before assuming UTC.
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // Fall through to a local timestamp interpreted as UTC.
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("timestamp primary key must be ISO-8601", exception);
        }
    }

    private static String normalizeType(String datatype) {
        if (datatype == null) {
            throw new IllegalArgumentException("datatype is required");
        }
        String normalized = datatype.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "timestamp with time zone", "timestamp_tz", "timestamp with zone" ->
                    "timestamptz";
            case "timestamp without time zone", "timestamp_ntz", "timestamp without zone" ->
                    "timestamp";
            default -> normalized;
        };
    }
}
