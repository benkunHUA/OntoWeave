package com.ontoweave.data.sync.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SyncRunStatusConverter implements AttributeConverter<SyncRunStatus, String> {
    @Override
    public String convertToDatabaseColumn(SyncRunStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SyncRunStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SyncRunStatus.from(dbData);
    }
}
