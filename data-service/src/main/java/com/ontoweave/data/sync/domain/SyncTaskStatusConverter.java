package com.ontoweave.data.sync.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SyncTaskStatusConverter implements AttributeConverter<SyncTaskStatus, String> {
    @Override
    public String convertToDatabaseColumn(SyncTaskStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SyncTaskStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SyncTaskStatus.from(dbData);
    }
}
