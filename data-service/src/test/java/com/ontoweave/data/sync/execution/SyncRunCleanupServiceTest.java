package com.ontoweave.data.sync.execution;

import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncRunCleanupServiceTest {

    @Mock
    DatasetVersionRepository versions;

    @Mock
    IcebergCatalogClient iceberg;

    @Test
    void dropsRunTableWhenNoVersionReferencesIt() {
        SyncRunEntity run = run();
        when(versions.existsByIcebergCatalogAndIcebergNamespaceAndIcebergTable(
                run.getIcebergCatalog(), run.getIcebergNamespace(), run.getIcebergTable()
        )).thenReturn(false);

        service().cleanupIfUnreferenced(run);

        verify(iceberg).dropTable(
                run.getIcebergCatalog(), run.getIcebergNamespace(), run.getIcebergTable()
        );
    }

    @Test
    void keepsRunTableWhenVersionReferencesIt() {
        SyncRunEntity run = run();
        when(versions.existsByIcebergCatalogAndIcebergNamespaceAndIcebergTable(
                run.getIcebergCatalog(), run.getIcebergNamespace(), run.getIcebergTable()
        )).thenReturn(true);

        service().cleanupIfUnreferenced(run);

        verify(iceberg, never()).dropTable(any(), any(), any());
    }

    private SyncRunCleanupService service() {
        return new SyncRunCleanupService(versions, iceberg);
    }

    private SyncRunEntity run() {
        return new SyncRunEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SyncMode.CDC,
                "job-name",
                "ontoweave",
                "db",
                "tbl",
                86400
        );
    }
}
