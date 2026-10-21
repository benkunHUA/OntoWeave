package com.ontoweave.data.sync.iceberg;

import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.CloseableIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CancellationException;

@Component
public class IcebergSnapshotMetrics {

    private static final Logger log = LoggerFactory.getLogger(IcebergSnapshotMetrics.class);

    private final HadoopCatalogFactory catalogs;

    public IcebergSnapshotMetrics(HadoopCatalogFactory catalogs) {
        this.catalogs = catalogs;
    }

    public Optional<Long> recordCount(String catalog, String namespace, String table, long snapshotId) {
        try {
            return catalogs.withTable(namespace, table, current -> recordCount(current, snapshotId));
        } catch (CancellationException error) {
            throw error;
        } catch (RuntimeException error) {
            log.warn(
                    "unable to read iceberg snapshot metrics for {}.{}.{} snapshot {}",
                    catalog,
                    namespace,
                    table,
                    snapshotId,
                    error
            );
            return Optional.empty();
        }
    }

    private Long recordCount(Table table, long snapshotId) throws Exception {
        if (table.snapshot(snapshotId) == null) {
            return null;
        }
        long total = 0L;
        try (CloseableIterable<FileScanTask> tasks = table.newScan()
                .useSnapshot(snapshotId)
                .planFiles()) {
            for (FileScanTask task : tasks) {
                Long count = task.file().recordCount();
                if (count == null) {
                    return null;
                }
                try {
                    total = Math.addExact(total, count);
                } catch (ArithmeticException error) {
                    return null;
                }
            }
        }
        return total;
    }
}
