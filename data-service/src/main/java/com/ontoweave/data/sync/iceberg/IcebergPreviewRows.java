package com.ontoweave.data.sync.iceberg;

import com.ontoweave.data.common.PreviewValues;
import org.apache.iceberg.data.Record;

import java.util.ArrayList;
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
            row.put(columns.get(index), PreviewValues.normalize(record.get(index)));
        }
        return row;
    }

}
