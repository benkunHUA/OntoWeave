package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineStatusConverter extends LowercaseEnumConverter<PipelineStatus> {

    public PipelineStatusConverter() {
        super(PipelineStatus.class);
    }
}
