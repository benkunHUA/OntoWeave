package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineBuildStageConverter extends LowercaseEnumConverter<PipelineBuildStage> {

    public PipelineBuildStageConverter() {
        super(PipelineBuildStage.class);
    }
}
