package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineBuildTriggerConverter extends LowercaseEnumConverter<PipelineBuildTrigger> {

    public PipelineBuildTriggerConverter() {
        super(PipelineBuildTrigger.class);
    }
}
