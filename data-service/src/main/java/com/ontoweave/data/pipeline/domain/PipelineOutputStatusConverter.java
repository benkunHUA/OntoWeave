package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineOutputStatusConverter extends LowercaseEnumConverter<PipelineOutputStatus> {

    public PipelineOutputStatusConverter() {
        super(PipelineOutputStatus.class);
    }
}
