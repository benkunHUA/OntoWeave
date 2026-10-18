package com.ontoweave.data.sync.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record TriggerSyncRequest(@NotNull UUID operationId, List<@Valid TableRef> tables) {
}
