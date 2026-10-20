package com.ontoweave.data.pipeline.domain;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PipelineScheduleStatusConverter extends LowercaseEnumConverter<PipelineScheduleStatus> {

    public PipelineScheduleStatusConverter() {
        super(PipelineScheduleStatus.class);
    }
}
