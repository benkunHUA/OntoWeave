package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineOutputTypeConverter extends LowercaseEnumConverter<PipelineOutputType> {

    public PipelineOutputTypeConverter() {
        super(PipelineOutputType.class);
    }
}
