package com.ontoweave.data.sync.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TableRef(@NotBlank String schema, @NotBlank String table) {
}
