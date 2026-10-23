package com.ontoweave.data.ontology.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class MappingIssues {

    private final List<MappingValidationResult.MappingIssue> blocking = new ArrayList<>();
    private final List<MappingValidationResult.MappingIssue> warnings = new ArrayList<>();

    void block(String code, String message, UUID attributeId, String column) {
        blocking.add(new MappingValidationResult.MappingIssue(code, message, attributeId, column));
    }

    void warn(String code, String message, UUID attributeId, String column) {
        warnings.add(new MappingValidationResult.MappingIssue(code, message, attributeId, column));
    }

    MappingValidationResult result() {
        return new MappingValidationResult(blocking, warnings);
    }
}
