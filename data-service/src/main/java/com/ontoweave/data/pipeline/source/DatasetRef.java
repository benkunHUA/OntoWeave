package com.ontoweave.data.pipeline.source;

import java.util.UUID;

public record DatasetRef(String datasetKind, UUID datasetId) {
}
