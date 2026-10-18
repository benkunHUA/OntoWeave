package com.ontoweave.data.sync.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SyncModeConverter implements AttributeConverter<SyncMode, String> {
    @Override
    public String convertToDatabaseColumn(SyncMode attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SyncMode convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SyncMode.from(dbData);
    }
}
