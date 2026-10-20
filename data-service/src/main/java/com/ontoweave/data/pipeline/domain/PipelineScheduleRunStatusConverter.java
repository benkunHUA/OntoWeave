package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineScheduleRunStatusConverter extends LowercaseEnumConverter<PipelineScheduleRunStatus> {

    public PipelineScheduleRunStatusConverter() {
        super(PipelineScheduleRunStatus.class);
    }
}
