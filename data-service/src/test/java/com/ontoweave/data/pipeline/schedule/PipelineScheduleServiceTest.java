package com.ontoweave.data.pipeline.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunStatus;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineScheduleServiceTest {

    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final PipelineScheduleRepository schedules = mock(PipelineScheduleRepository.class);
    private final PipelineScheduleRunRepository runs = mock(PipelineScheduleRunRepository.class);
    private final PipelineScheduleDispatcher dispatcher = mock(PipelineScheduleDispatcher.class);
    private final PipelineScheduleMapper mapper = new PipelineScheduleMapper();
    private final PipelineScheduleServiceImpl service = new PipelineScheduleServiceImpl(
            pipelines, schedules, runs, dispatcher, new CronScheduleCalculator(), mapper
    );

    private UUID pipelineId;
    private UUID ownerId;
    private PipelineEntity pipeline;

    @BeforeEach
    void setUp() {
        pipelineId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        pipeline = new PipelineEntity(pipelineId, ownerId, UUID.randomUUID(), "pipeline");
        when(pipelines.findByIdAndOwnerId(pipelineId, ownerId)).thenReturn(Optional.of(pipeline));
        when(schedules.save(any(PipelineScheduleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void putCreatesAnActiveUtcScheduleFromTheRequestedTimeZone() {
        when(schedules.lockByPipelineId(pipelineId)).thenReturn(Optional.empty());

        ScheduleResponse response = service.put(
                pipelineId, ownerId, new ScheduleRequest("0 9 * * *", "Asia/Shanghai", "morning")
        );

        assertThat(response.status()).isEqualTo("active");
        assertThat(response.triggerType()).isEqualTo("time");
        assertThat(response.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(response.nextRunAt()).isAfter(Instant.now());
        assertThat(response.name()).isEqualTo("morning");
    }

    @Test
    void pauseMarksPendingIgnoredAndClearsNextRun() {
        PipelineScheduleEntity schedule = schedule(Instant.now().plusSeconds(60));
        PipelineScheduleRunEntity pending = new PipelineScheduleRunEntity(
                UUID.randomUUID(), schedule.getId(), pipelineId,
                PipelineScheduleRunStatus.PENDING, "time", Instant.now(), new ObjectMapper().createArrayNode()
        );
        when(schedules.lockByPipelineId(pipelineId)).thenReturn(Optional.of(schedule));
        when(runs.lockPendingByScheduleId(schedule.getId())).thenReturn(List.of(pending));

        ScheduleResponse response = service.pause(pipelineId, ownerId);

        assertThat(response.status()).isEqualTo("paused");
        assertThat(response.nextRunAt()).isNull();
        assertThat(pending.getStatus()).isEqualTo(PipelineScheduleRunStatus.IGNORED);
        assertThat(pending.getReasonCode()).isEqualTo("SCHEDULE_PAUSED");
    }

    @Test
    void resumeCalculatesNextRunFromNowWithoutReplayingMissedRuns() {
        PipelineScheduleEntity schedule = schedule(Instant.now().plusSeconds(60));
        schedule.pause(ownerId, Instant.now().minusSeconds(3600));
        when(schedules.lockByPipelineId(pipelineId)).thenReturn(Optional.of(schedule));

        ScheduleResponse response = service.resume(pipelineId, ownerId);

        assertThat(response.status()).isEqualTo("active");
        assertThat(response.nextRunAt()).isAfter(Instant.now());
        assertThat(response.pausedAt()).isNull();
    }

    @Test
    void rejectsRequestsForAnotherOwner() {
        assertThatThrownBy(() -> service.get(pipelineId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }

    private PipelineScheduleEntity schedule(Instant nextRunAt) {
        return new PipelineScheduleEntity(
                UUID.randomUUID(), pipelineId, "schedule", "*/5 * * * *", "UTC",
                nextRunAt, ownerId
        );
    }
}
