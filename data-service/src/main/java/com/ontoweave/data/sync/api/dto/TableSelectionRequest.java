package com.ontoweave.data.sync.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TableSelectionRequest(
        @NotBlank String schema,
        @NotBlank String table,
        String syncMode,
        @Min(60) Integer versionIntervalSeconds
) {
}
