package com.ontoweave.data.pipeline.service;

import java.time.Instant;
import java.util.UUID;

public record PipelineResponse(
        UUID id,
        UUID conversationId,
        String name,
        String status,
        Long draftRevision,
        UUID currentVersionId,
        Integer currentVersionNumber,
        Long currentVersionDraftRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
