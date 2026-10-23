package com.ontoweave.data.ontology.mapping;

import java.util.Locale;
import java.util.Set;

final class MappingSubjectStatus {

    private static final Set<String> CONFIRMED = Set.of("confirmed", "approved");

    private MappingSubjectStatus() {
    }

    static boolean isConfirmed(String subjectStatus) {
        String normalized = subjectStatus == null
                ? ""
                : subjectStatus.trim().toLowerCase(Locale.ROOT);
        return CONFIRMED.contains(normalized);
    }
}
