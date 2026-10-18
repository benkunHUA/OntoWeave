package com.ontoweave.data.connection.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

public record UpdateDataConnectionRequest(
        @Size(min = 1, max = 200) String name,
        JsonNode config,
        JsonNode secret
) {
}
