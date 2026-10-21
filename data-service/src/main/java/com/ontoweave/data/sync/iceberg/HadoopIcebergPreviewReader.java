package com.ontoweave.data.sync.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.springframework.stereotype.Component;

import java.util.List;
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
        List<PreviewColumn> columns = schema.columns().stream()
                .map(field -> new PreviewColumn(
                        field.name(),
                        field.type().toString(),
                        field.isOptional()
                ))
                .toList();
        try (CloseableIterable<Record> records = IcebergGenerics.read(table)
                .useSnapshot(snapshotId)
                .project(schema)
                .build()) {
            return IcebergPreviewRows.read(records, columns, limit);
        }
    }
}
