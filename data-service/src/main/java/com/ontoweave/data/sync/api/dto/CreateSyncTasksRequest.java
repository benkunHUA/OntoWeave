package com.ontoweave.data.sync.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateSyncTasksRequest(@NotEmpty List<@Valid TableSelectionRequest> tables) {
}
