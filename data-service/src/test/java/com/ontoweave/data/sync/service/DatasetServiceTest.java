package com.ontoweave.data.sync.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.connection.service.DataConnectionService;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.VersionSource;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock
    DataConnectionService connections;

    @Mock
    DatasetRepository datasets;

    @Mock
    DatasetVersionRepository versions;

    @Mock
    SyncTaskRepository tasks;

    @Test
    void passesArbitraryOffsetToVersionQuery() {
        UUID datasetId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        DatasetEntity dataset = new DatasetEntity(datasetId, connectionId, "demo.orders");
        DatasetVersionEntity version = version(datasetId);
        when(datasets.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(versions.findPageByDatasetId(datasetId, 20, 5)).thenReturn(List.of(version));
        when(versions.countByDatasetId(datasetId)).thenReturn(42L);

        var response = service().versions(datasetId, ownerId, 20, 5);

        assertThat(response.total()).isEqualTo(42);
        assertThat(response.limit()).isEqualTo(20);
        assertThat(response.offset()).isEqualTo(5);
        assertThat(response.versions()).hasSize(1);
        assertThat(response.versions().get(0).versionNumber()).isEqualTo(7);
    }

    private DatasetService service() {
        return new DatasetService(connections, datasets, versions, tasks);
    }

    private DatasetVersionEntity version(UUID datasetId) {
        return new DatasetVersionEntity(
                UUID.randomUUID(),
                datasetId,
                7,
                "ontoweave",
                "ns",
                "tbl",
                42L,
                10L,
                false,
                100L,
                JsonNodeFactory.instance.objectNode(),
                1,
                Instant.now(),
                VersionSource.SCHEDULED,
                UUID.randomUUID(),
                "file:/tmp/table"
        );
    }
}
