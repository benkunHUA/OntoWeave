package com.ontoweave.data.connection.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter(autoApply = true)
public class ConnectionStatusConverter implements AttributeConverter<ConnectionStatus, String> {

    @Override
    public String convertToDatabaseColumn(ConnectionStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ConnectionStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ConnectionStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
