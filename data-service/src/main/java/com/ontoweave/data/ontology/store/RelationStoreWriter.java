package com.ontoweave.data.ontology.store;

import java.util.List;
import java.util.UUID;

public interface RelationStoreWriter {

    void write(UUID runId, List<RelationStageRow> rows);

    StageValidation validate(UUID runId);

    void promote(UUID runId, UUID versionId);

    void deleteStage(UUID runId);

    void deleteVersion(UUID versionId);

    record RelationStageRow(
            UUID relationId,
            UUID fromObjectTypeId,
            String fromPrimaryKey,
            UUID toObjectTypeId,
            String toPrimaryKey
    ) {
    }
}
