package com.ontoweave.data.sync.execution;

import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 清理未被任何数据集版本引用的运行表，保证失败/停止后的清理幂等。 */
@Service
public class SyncRunCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SyncRunCleanupService.class);

    private final DatasetVersionRepository versions;
    private final IcebergCatalogClient iceberg;

    public SyncRunCleanupService(DatasetVersionRepository versions, IcebergCatalogClient iceberg) {
        this.versions = versions;
        this.iceberg = iceberg;
    }

    public void cleanupIfUnreferenced(SyncRunEntity run) {
        if (versions.existsByIcebergCatalogAndIcebergNamespaceAndIcebergTable(
                run.getIcebergCatalog(),
                run.getIcebergNamespace(),
                run.getIcebergTable()
        )) {
            return;
        }
        try {
            iceberg.dropTable(run.getIcebergCatalog(), run.getIcebergNamespace(), run.getIcebergTable());
        } catch (RuntimeException exception) {
            log.warn("failed to clean up iceberg table {}.{} for run {}",
                    run.getIcebergNamespace(), run.getIcebergTable(), run.getId(), exception);
        }
    }
}
