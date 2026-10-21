package com.ontoweave.data.pipeline.execution.output;

import java.util.UUID;

public final class OutputTableNames {

    public static final String NAMESPACE = "pipeline";

    private OutputTableNames() {
    }

    public static String table(UUID datasetId) {
        return "pipeline_" + datasetId.toString().replace("-", "");
    }
}
