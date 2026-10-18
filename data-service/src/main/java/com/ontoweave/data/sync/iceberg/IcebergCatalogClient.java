package com.ontoweave.data.sync.iceberg;

import java.util.Optional;

public interface IcebergCatalogClient {

    Optional<IcebergSnapshotInfo> latestSnapshot(String catalog, String namespace, String table);

    Optional<IcebergSnapshotInfo> createEmptySnapshot(String catalog, String namespace, String table);

    void createTag(String catalog, String namespace, String table, long snapshotId, String tag);

    void removeTag(String catalog, String namespace, String table, String tag);

    void dropTable(String catalog, String namespace, String table);
}
