package com.ontoweave.data.connection.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record DataConnectionResponse(
        UUID id,
        UUID ownerId,
        UUID conversationId,
        String name,
        String kind,
        JsonNode config,
        int configVersion,
        String status,
        String lastTestStatus,
        Instant lastTestAt,
        String lastTestError,
        JsonNode lastTestDetails,
        boolean secretConfigured,
        String lastSyncStatus,
        Instant lastSyncAt,
        Instant createdAt,
        Instant updatedAt
) {
}
