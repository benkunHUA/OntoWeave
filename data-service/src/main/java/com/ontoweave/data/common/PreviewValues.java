package com.ontoweave.data.common;

import org.apache.flink.types.Row;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Types;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PreviewValues {

    private PreviewValues() {
    }

    public static Object normalize(Object value) {
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof ByteBuffer buffer) {
            ByteBuffer copy = buffer.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Row row) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int index = 0; index < row.getArity(); index++) {
                values.put("field_" + index, normalize(row.getField(index)));
            }
            return values;
        }
        if (value instanceof Record record) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Types.NestedField field : record.struct().fields()) {
                values.put(field.name(), normalize(record.getField(field.name())));
            }
            return values;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, item) -> values.put(String.valueOf(key), normalize(item)));
            return values;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(PreviewValues::normalize).toList();
        }
        return value;
    }
}
