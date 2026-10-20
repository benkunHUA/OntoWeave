package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineDatasetWriteModeConverter extends LowercaseEnumConverter<PipelineDatasetWriteMode> {

    public PipelineDatasetWriteModeConverter() {
        super(PipelineDatasetWriteMode.class);
    }
}
