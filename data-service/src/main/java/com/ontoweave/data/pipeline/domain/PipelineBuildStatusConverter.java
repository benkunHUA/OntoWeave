package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineBuildStatusConverter extends LowercaseEnumConverter<PipelineBuildStatus> {

    public PipelineBuildStatusConverter() {
        super(PipelineBuildStatus.class);
    }
}
