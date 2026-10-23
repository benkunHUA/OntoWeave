package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.common.RelationKind;
import com.ontoweave.data.ontology.common.VersionStatus;

import java.time.Instant;
import java.util.UUID;

public record RelationVersionSummary(
        UUID id,
        RelationKind relationKind,
        VersionStatus status,
        long rowCount,
        long skippedCount,
        UUID runId,
        Instant activatedAt
) {
}
