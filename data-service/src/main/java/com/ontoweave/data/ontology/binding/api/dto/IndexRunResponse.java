package com.ontoweave.data.ontology.binding.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.RunPhase;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.common.TriggerType;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;

import java.time.Instant;
import java.util.UUID;

public record IndexRunResponse(
        UUID id,
        UUID ownerId,
        SubjectKind subjectKind,
        UUID subjectId,
        UUID bindingId,
        TriggerType triggerType,
        RunStatus status,
        RunPhase phase,
        DatasetKind targetDatasetKind,
        UUID targetDatasetVersionId,
        long mappingRevision,
        Instant requestedAt,
        Instant startedAt,
        Instant heartbeatAt,
        Instant finishedAt,
        String errorCode,
        String errorMessage,
        JsonNode stats
) {

    public static IndexRunResponse from(OntologyIndexRunEntity run) {
        return new IndexRunResponse(
                run.getId(),
                run.getOwnerId(),
                run.getSubjectKind(),
                run.getSubjectId(),
                run.getBindingId(),
                run.getTriggerType(),
                run.getStatus(),
                run.getPhase(),
                run.getTargetDatasetKind(),
                run.getTargetDatasetVersionId(),
                run.getMappingRevision(),
                run.getRequestedAt(),
                run.getStartedAt(),
                run.getHeartbeatAt(),
                run.getFinishedAt(),
                run.getErrorCode(),
                run.getErrorMessage(),
                run.getStats()
        );
    }
}
