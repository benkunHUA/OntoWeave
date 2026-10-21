package com.ontoweave.data.sync.repository;

import java.util.UUID;

public interface DatasetVersionCount {

    UUID getDatasetId();

    long getVersionCount();
}
