package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.pipeline.execution.build.ResolvedInput;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ScheduleLogicHash {

    private ScheduleLogicHash() {
    }

    public static String compute(String definitionHash, List<ResolvedInput> inputs) {
        String versions = (inputs == null ? List.<ResolvedInput>of() : inputs).stream()
                .filter(Objects::nonNull)
                .map(ResolvedInput::datasetVersionId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        return (definitionHash == null ? "" : definitionHash) + ":" + versions;
    }
}
