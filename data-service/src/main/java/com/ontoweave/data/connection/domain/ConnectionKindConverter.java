package com.ontoweave.data.connection.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ConnectionKindConverter implements AttributeConverter<ConnectionKind, String> {

    @Override
    public String convertToDatabaseColumn(ConnectionKind attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ConnectionKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ConnectionKind.from(dbData);
    }
}
