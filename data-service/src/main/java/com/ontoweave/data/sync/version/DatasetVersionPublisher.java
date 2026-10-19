package com.ontoweave.data.sync.version;

import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.sync.domain.DatasetEntity;
import com.ontoweave.data.sync.domain.DatasetVersionEntity;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.VersionSource;
import com.ontoweave.data.sync.iceberg.IcebergCatalogClient;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotInfo;
import com.ontoweave.data.sync.repository.DatasetRepository;
import com.ontoweave.data.sync.repository.DatasetVersionRepository;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatasetVersionPublisher {

    private final DatasetRepository datasets;
    private final DatasetVersionRepository versions;
    private final SyncRunRepository runs;
    private final IcebergCatalogClient iceberg;
    private final OutboxWriter outbox;

    public DatasetVersionPublisher(
            DatasetRepository datasets,
            DatasetVersionRepository versions,
            SyncRunRepository runs,
            IcebergCatalogClient iceberg,
            OutboxWriter outbox
    ) {
        this.datasets = datasets;
        this.versions = versions;
        this.runs = runs;
        this.iceberg = iceberg;
        this.outbox = outbox;
    }

    @Transactional
    public Optional<DatasetVersionEntity> publish(UUID runId, VersionSource source) {
        SyncRunEntity run = runs.findById(runId).orElse(null);
        if (run == null) {
            return Optional.empty();
        }
        DatasetEntity dataset = datasets.findForUpdate(run.getDatasetId()).orElseThrow();
        Optional<IcebergSnapshotInfo> snapshot = iceberg.latestSnapshot(
                run.getIcebergCatalog(),
                run.getIcebergNamespace(),
                run.getIcebergTable()
        );
        if (snapshot.isEmpty() && run.getRunMode() == SyncMode.SNAPSHOT) {
            snapshot = iceberg.createEmptySnapshot(
                    run.getIcebergCatalog(),
                    run.getIcebergNamespace(),
                    run.getIcebergTable()
            );
        }
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        IcebergSnapshotInfo info = snapshot.get();
        if (run.getLastPublishedSnapshotId() != null && run.getLastPublishedSnapshotId() == info.snapshotId()) {
            return Optional.empty();
        }
        Optional<DatasetVersionEntity> existing = versions
                .findByDatasetIdAndIcebergTableAndIcebergSnapshotId(
                        dataset.getId(),
                        run.getIcebergTable(),
                        info.snapshotId()
                );
        if (existing.isPresent()) {
            markPublished(run, info.snapshotId());
            return existing;
        }
        int versionNumber = versions.findTopByDatasetIdOrderByVersionNumberDesc(dataset.getId())
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1);
        String tag = "ontoweave-version-" + dataset.getId() + "-" + versionNumber;
        iceberg.createTag(run.getIcebergCatalog(), run.getIcebergNamespace(), run.getIcebergTable(), info.snapshotId(), tag);
        try {
            DatasetVersionEntity version = new DatasetVersionEntity(
                    UUID.randomUUID(),
                    dataset.getId(),
                    versionNumber,
                    run.getIcebergCatalog(),
                    run.getIcebergNamespace(),
                    run.getIcebergTable(),
                    info.snapshotId(),
                    info.rowCount(),
                    run.getRunMode() == SyncMode.CDC,
                    info.byteSize(),
                    info.schema(),
                    info.columnCount(),
                    info.committedAt(),
                    source
            );
            version = versions.save(version);
            dataset.activateVersion(
                    version.getId(),
                    run.getIcebergCatalog(),
                    run.getIcebergNamespace(),
                    run.getIcebergTable()
            );
            datasets.save(dataset);
            markPublished(run, info.snapshotId());
            outbox.write("dataset", dataset.getId(), "dataset.version.published", payload(dataset, version, run));
            return Optional.of(version);
        } catch (RuntimeException exception) {
            iceberg.removeTag(run.getIcebergCatalog(), run.getIcebergNamespace(), run.getIcebergTable(), tag);
            throw exception;
        }
    }

    private void markPublished(SyncRunEntity run, long snapshotId) {
        Instant now = Instant.now();
        runs.markSnapshotPublished(
                run.getId(),
                snapshotId,
                now.plusSeconds(run.getVersionIntervalSeconds()),
                now
        );
        run.recordPublishedSnapshot(snapshotId);
    }

    private Map<String, Object> payload(
            DatasetEntity dataset,
            DatasetVersionEntity version,
            SyncRunEntity run
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", dataset.getId());
        payload.put("datasetName", dataset.getName());
        payload.put("versionId", version.getId());
        payload.put("versionNumber", version.getVersionNumber());
        payload.put("rowCount", version.getRowCount());
        payload.put("icebergSnapshotId", version.getIcebergSnapshotId());
        payload.put("runId", run.getId());
        payload.put("operationId", run.getOperationId());
        payload.put("connectionId", dataset.getConnectionId());
        payload.put("summary", dataset.getName() + " 发布版本 v" + version.getVersionNumber());
        return payload;
    }
}
