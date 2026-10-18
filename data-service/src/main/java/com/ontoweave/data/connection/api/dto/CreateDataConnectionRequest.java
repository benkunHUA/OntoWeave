package com.ontoweave.data.connection.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDataConnectionRequest(
        @NotNull UUID ownerId,
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String kind,
        JsonNode config,
        JsonNode secret
) {
}
