package com.ontoweave.data.ontology.store;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.SubjectKind;

import java.util.UUID;

public interface IndexEnqueuer {

    UUID enqueueManual(ManualIndexRequest request);

    UUID enqueueOnPublish(DatasetKind kind, UUID datasetId, UUID versionId);

    UUID enqueueRetry(UUID runId);

    record ManualIndexRequest(
            UUID ownerId,
            SubjectKind subjectKind,
            UUID subjectId,
            UUID bindingId,
            DatasetKind datasetKind,
            UUID datasetVersionId,
            MappingSnapshot mappingSnapshot,
            long mappingRevision
    ) {
    }
}
