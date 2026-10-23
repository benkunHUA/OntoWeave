package com.ontoweave.data.ontology.store;

import java.util.List;

public record StageValidation(
        boolean valid,
        long rowCount,
        long duplicateKeyCount,
        long requiredMissingCount,
        List<String> samples
) {

    public StageValidation {
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
