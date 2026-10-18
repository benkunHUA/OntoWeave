package com.ontoweave.data.connection.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter(autoApply = true)
public class TestStatusConverter implements AttributeConverter<TestStatus, String> {

    @Override
    public String convertToDatabaseColumn(TestStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public TestStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TestStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
