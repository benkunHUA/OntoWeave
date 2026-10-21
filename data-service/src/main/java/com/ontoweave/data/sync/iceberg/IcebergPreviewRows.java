package com.ontoweave.data.sync.iceberg;

import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Types;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

final class IcebergPreviewRows {

    private IcebergPreviewRows() {
    }

    static IcebergPreview read(
            Iterable<Record> records,
            List<PreviewColumn> columns,
            int limit
    ) {
        List<String> columnNames = columns.stream().map(PreviewColumn::name).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        Iterator<Record> iterator = records.iterator();
        while (true) {
            ensureNotInterrupted();
            if (!iterator.hasNext()) {
                break;
            }
            Record record = iterator.next();
            if (rows.size() >= limit) {
                truncated = true;
                break;
            }
            rows.add(row(columnNames, record));
            ensureNotInterrupted();
        }
        return new IcebergPreview(columns, rows, truncated);
    }

    private static void ensureNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Iceberg preview read interrupted");
        }
    }

    private static Map<String, Object> row(List<String> columns, Record record) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            row.put(columns.get(index), normalize(record.get(index)));
        }
        return row;
    }

    private static Object normalize(Object value) {
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof ByteBuffer buffer) {
            ByteBuffer copy = buffer.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Record record) {
            return record(record);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), normalize(item)));
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(IcebergPreviewRows::normalize).toList();
        }
        return value;
    }

    private static Map<String, Object> record(Record record) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Types.NestedField field : record.struct().fields()) {
            normalized.put(field.name(), normalize(record.getField(field.name())));
        }
        return normalized;
    }
}
