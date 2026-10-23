package com.ontoweave.data.ontology.iceberg;

import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.sync.iceberg.HadoopCatalogFactory;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class IcebergSnapshotReader {

    private final HadoopCatalogFactory catalogs;

    public IcebergSnapshotReader(HadoopCatalogFactory catalogs) {
        this.catalogs = catalogs;
    }

    public SnapshotRead open(DatasetRef dataset, long snapshotId) {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset is required");
        }
        SnapshotRead read = catalogs.withTable(
                dataset.namespace(),
                dataset.table(),
                table -> openSnapshot(table, snapshotId)
        ).orElseThrow(() -> new IcebergSnapshotNotFoundException(
                dataset.namespace() + "." + dataset.table(),
                snapshotId
        ));
        return read;
    }

    private SnapshotRead openSnapshot(Table table, long snapshotId) {
        Snapshot snapshot = table.snapshot(snapshotId);
        if (snapshot == null) {
            return null;
        }
        Schema schema = table.schemas().get((int) snapshot.schemaId());
        if (schema == null) {
            schema = table.schema();
        }
        CloseableIterable<Record> records = IcebergGenerics.read(table)
                .useSnapshot(snapshotId)
                .project(schema)
                .build();
        return new SnapshotRead(schema, records);
    }

    public record SnapshotRead(
            Schema schema,
            CloseableIterable<Record> records
    ) implements AutoCloseable {

        @Override
        public void close() {
            try {
                records.close();
            } catch (IOException exception) {
                throw new IcebergSnapshotReadException("failed to close snapshot reader", exception);
            }
        }
    }

    public static class IcebergSnapshotNotFoundException extends RuntimeException {

        public IcebergSnapshotNotFoundException(String table, long snapshotId) {
            super("Iceberg snapshot not found: " + table + "@" + snapshotId);
        }
    }

    public static class IcebergSnapshotReadException extends RuntimeException {

        public IcebergSnapshotReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
