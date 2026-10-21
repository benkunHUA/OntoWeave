package com.ontoweave.data.pipeline.schedule;

import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineScheduleMapperTest {

    private final PipelineScheduleMapper mapper = new PipelineScheduleMapper();

    @Test
    void mapsScheduleRunTimingForBrowserDiagnostics() {
        PipelineScheduleRunEntity run = mock(PipelineScheduleRunEntity.class);
        Instant started = Instant.parse("2026-10-21T01:00:00Z");
        Instant finished = Instant.parse("2026-10-21T01:00:03Z");
        when(run.getId()).thenReturn(UUID.randomUUID());
        when(run.getScheduleId()).thenReturn(UUID.randomUUID());
        when(run.getPipelineId()).thenReturn(UUID.randomUUID());
        when(run.getScheduledFor()).thenReturn(started);
        when(run.getStartedAt()).thenReturn(started);
        when(run.getFinishedAt()).thenReturn(finished);

        ScheduleRunResponse response = mapper.toRunResponse(run);

        assertThat(response.startedAt()).isEqualTo(started);
        assertThat(response.finishedAt()).isEqualTo(finished);
    }
}
