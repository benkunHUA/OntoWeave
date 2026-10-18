package com.ontoweave.data.sync.iceberg;

import java.util.Optional;

public interface IcebergPreviewReader {

    Optional<IcebergPreview> read(
            String catalog,
            String namespace,
            String table,
            long snapshotId,
            int limit
    );
}
