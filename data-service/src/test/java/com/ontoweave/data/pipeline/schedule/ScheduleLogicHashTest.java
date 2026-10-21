package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleLogicHashTest {

    @Test
    void sortsAndDeduplicatesResolvedInputVersionIds() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        String left = ScheduleLogicHash.compute("definition", List.of(input(second), input(first), input(second)));
        String right = ScheduleLogicHash.compute("definition", List.of(input(first), input(second)));

        assertThat(left).isEqualTo(right);
        assertThat(left).isEqualTo("definition:" + first + "," + second);
    }

    @Test
    void changesWhenThePublishedDefinitionChanges() {
        ResolvedInput input = input(UUID.randomUUID());

        assertThat(ScheduleLogicHash.compute("v1", List.of(input)))
                .isNotEqualTo(ScheduleLogicHash.compute("v2", List.of(input)));
    }

    private ResolvedInput input(UUID versionId) {
        return new ResolvedInput(
                "sync", UUID.randomUUID(), versionId, 1, "schema",
                "catalog", "namespace", "table", 1L, null
        );
    }
}
