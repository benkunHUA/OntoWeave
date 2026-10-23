package com.ontoweave.data.ontology.store;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ObjectStoreWriter {

    void write(UUID runId, List<ObjectStageRow> rows);

    StageValidation validate(UUID runId);

    void promote(UUID runId, UUID versionId);

    void deleteStage(UUID runId);

    void deleteVersion(UUID versionId);

    record ObjectStageRow(
            UUID objectTypeId,
            String primaryKeyValue,
            BigDecimal primaryKeyNum,
            LocalDate primaryKeyDate,
            Instant primaryKeyTs,
            String titleValue,
            JsonNode properties,
            List<ObjectValueStageRow> values
    ) {

        public ObjectStageRow {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    record ObjectValueStageRow(
            UUID attributeId,
            String primaryKeyValue,
            String textValue,
            BigDecimal numValue,
            LocalDate dateValue,
            Instant tsValue,
            Boolean boolValue
    ) {
    }
}
