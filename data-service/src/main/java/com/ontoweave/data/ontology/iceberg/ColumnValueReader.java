package com.ontoweave.data.ontology.iceberg;

import org.apache.iceberg.data.Record;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class ColumnValueReader {

    public Object read(Record record, String column) {
        return normalize(record.getField(column));
    }

    public Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof LocalDate date) {
            return date;
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
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return value;
    }
}
