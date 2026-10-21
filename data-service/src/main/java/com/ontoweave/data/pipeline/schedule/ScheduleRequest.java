package com.ontoweave.data.pipeline.schedule;

import jakarta.validation.constraints.NotBlank;

public record ScheduleRequest(
        @NotBlank String cronExpression,
        @NotBlank String timezone,
        String name
) {
}
