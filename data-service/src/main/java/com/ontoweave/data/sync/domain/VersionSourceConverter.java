package com.ontoweave.data.sync.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VersionSourceConverter implements AttributeConverter<VersionSource, String> {
    @Override
    public String convertToDatabaseColumn(VersionSource attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public VersionSource convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (VersionSource source : VersionSource.values()) {
            if (source.value().equals(dbData)) {
                return source;
            }
        }
        return VersionSource.SNAPSHOT_END;
    }
}
