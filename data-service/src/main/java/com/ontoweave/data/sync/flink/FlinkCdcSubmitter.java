package com.ontoweave.data.sync.flink;

import java.util.Optional;

public interface FlinkCdcSubmitter {

    SubmitResult submit(PipelineSpec spec);

    record SubmitResult(Optional<String> jobId, String output) {
    }
}
