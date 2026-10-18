package com.ontoweave.data.sync.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.types.Types;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class HadoopIcebergPreviewReader implements IcebergPreviewReader {

    private final HadoopCatalogFactory catalogs;

    public HadoopIcebergPreviewReader(HadoopCatalogFactory catalogs) {
        this.catalogs = catalogs;
    }

    @Override
    public Optional<IcebergPreview> read(
            String catalog,
            String namespace,
            String table,
            long snapshotId,
            int limit
    ) {
        return catalogs.withTable(namespace, table, current -> preview(current, snapshotId, limit));
    }

    private IcebergPreview preview(Table table, long snapshotId, int limit) throws Exception {
        Snapshot snapshot = table.snapshot(snapshotId);
        if (snapshot == null) {
            return null;
        }
        Schema schema = table.schemas().get((int) snapshot.schemaId());
        if (schema == null) {
            schema = table.schema();
        }
        List<String> columns = schema.columns().stream().map(Types.NestedField::name).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        try (CloseableIterable<Record> records = IcebergGenerics.read(table)
                .useSnapshot(snapshotId)
                .project(schema)
                .build()) {
            for (Record record : records) {
                if (rows.size() >= limit) {
                    truncated = true;
                    break;
                }
                rows.add(row(columns, record));
            }
        }
        return new IcebergPreview(columns, rows, truncated);
    }

    private Map<String, Object> row(List<String> columns, Record record) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            row.put(columns.get(index), normalize(record.get(index)));
        }
        return row;
    }

    private Object normalize(Object value) {
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
            return collection.stream().map(this::normalize).toList();
        }
        return value;
    }

    private Map<String, Object> record(Record record) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Types.NestedField field : record.struct().fields()) {
            normalized.put(field.name(), normalize(record.getField(field.name())));
        }
        return normalized;
    }
}
