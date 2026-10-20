package com.ontoweave.data.pipeline.domain;

public enum PipelineBuildStage {
    RESOLVING_INPUTS,
    SUBMITTING,
    RUNNING,
    CHECKING_EXPECTATIONS,
    PUBLISHING,
    FINISHED
}
