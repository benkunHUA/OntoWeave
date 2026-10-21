package com.ontoweave.data.pipeline.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineBuildTrigger;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleRunEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.execution.build.EnqueueScheduledBuild;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildService;
import com.ontoweave.data.pipeline.execution.build.PipelineInputResolver;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRepository;
import com.ontoweave.data.pipeline.repository.PipelineScheduleRunRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineScheduleDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
    private final PipelineInputResolver inputs = mock(PipelineInputResolver.class);
    private final PipelineBuildRepository builds = mock(PipelineBuildRepository.class);
    private final PipelineBuildService buildService = mock(PipelineBuildService.class);
    private final PipelineScheduleRepository schedules = mock(PipelineScheduleRepository.class);
    private final PipelineScheduleRunRepository runs = mock(PipelineScheduleRunRepository.class);
    private final PipelineScheduleDispatcher dispatcher = new PipelineScheduleDispatcher(
            pipelines, versions, inputs, builds, buildService, schedules, runs, mapper
    );

    private UUID pipelineId;
    private UUID versionId;
    private PipelineVersionEntity version;
    private PipelineScheduleEntity schedule;
    private Instant dueAt;

    @BeforeEach
    void setUp() {
        pipelineId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        dueAt = Instant.parse("2026-03-07T10:00:00Z");
        PipelineEntity pipeline = new PipelineEntity(
                pipelineId, UUID.randomUUID(), UUID.randomUUID(), "pipeline"
        );
        pipeline.activateVersion(versionId);
        version = new PipelineVersionEntity(
                versionId, pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode(),
                "definition-hash", mapper.createArrayNode(), 7, UUID.randomUUID()
        );
        schedule = new PipelineScheduleEntity(
                UUID.randomUUID(), pipelineId, "daily", "0 9 * * *", "UTC", dueAt,
                pipeline.getOwnerId()
        );
        when(schedules.lockById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(pipelines.findById(pipelineId)).thenReturn(Optional.of(pipeline));
        when(versions.findByIdAndPipelineId(versionId, pipelineId)).thenReturn(Optional.of(version));
        when(inputs.resolve(version)).thenReturn(List.of());
        when(runs.findByScheduleIdAndScheduledFor(schedule.getId(), dueAt))
                .thenReturn(Optional.empty());
        when(runs.findFirstByScheduleIdAndStatusOrderByScheduledForAsc(
                schedule.getId(), com.ontoweave.data.pipeline.domain.PipelineScheduleRunStatus.PENDING
        )).thenReturn(Optional.empty());
        when(runs.save(any(PipelineScheduleRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void skipsWhenPublishedVersionAndInputsAreUnchanged() {
        String hash = ScheduleLogicHash.compute("definition-hash", List.of());
        PipelineBuildEntity build = build(PipelineBuildStatus.SUCCEEDED, hash);
        when(builds.findFirstByPipelineIdAndPipelineVersionIdAndStatusInOrderByCreatedAtDescIdDesc(
                pipelineId, versionId, List.of(
                        PipelineBuildStatus.SUCCEEDED, PipelineBuildStatus.FAILED, PipelineBuildStatus.CANCELED
                )
        )).thenReturn(Optional.of(build));

        var run = dispatcher.dispatch(schedule, dueAt);

        assertThat(run.status()).isEqualTo("ignored");
        assertThat(run.reasonCode()).isEqualTo("NO_CHANGE");
        assertThat(run.logicHash()).isEqualTo(hash);
        verify(buildService, never()).enqueueScheduled(any());
    }

    @Test
    void dispatchesWithStableOperationIdWhenLatestTerminalBuildFailed() {
        String hash = ScheduleLogicHash.compute("definition-hash", List.of());
        when(builds.findFirstByPipelineIdAndPipelineVersionIdAndStatusInOrderByCreatedAtDescIdDesc(
                pipelineId, versionId, List.of(
                        PipelineBuildStatus.SUCCEEDED, PipelineBuildStatus.FAILED, PipelineBuildStatus.CANCELED
                )
        )).thenReturn(Optional.of(build(PipelineBuildStatus.FAILED, "old-hash")));
        when(builds.existsByPipelineIdAndStatusIn(any(), any())).thenReturn(false);
        UUID buildId = UUID.randomUUID();
        when(buildService.enqueueScheduled(any())).thenReturn(buildId);

        var run = dispatcher.dispatch(schedule, dueAt);

        ArgumentCaptor<EnqueueScheduledBuild> command = ArgumentCaptor.forClass(EnqueueScheduledBuild.class);
        verify(buildService).enqueueScheduled(command.capture());
        assertThat(run.status()).isEqualTo("dispatched");
        assertThat(run.buildId()).isEqualTo(buildId);
        assertThat(run.logicHash()).isEqualTo(hash);
        assertThat(command.getValue().operationId())
                .isEqualTo(ScheduleOperationIds.forRun(schedule.getId(), dueAt));
        assertThat(command.getValue().pipelineVersionId()).isEqualTo(versionId);
        assertThat(command.getValue().requestedBy()).isEqualTo(schedule.getCreatedBy());
        assertThat(command.getValue().resolvedInputs()).isEmpty();
        verify(inputs).resolve(version);
    }

    @Test
    void keepsPendingWithoutEnqueueingWhileAnotherBuildIsActive() {
        when(builds.findFirstByPipelineIdAndPipelineVersionIdAndStatusInOrderByCreatedAtDescIdDesc(
                pipelineId, versionId, List.of(
                        PipelineBuildStatus.SUCCEEDED, PipelineBuildStatus.FAILED, PipelineBuildStatus.CANCELED
                )
        )).thenReturn(Optional.empty());
        when(builds.existsByPipelineIdAndStatusIn(any(), any())).thenReturn(true);

        var run = dispatcher.dispatch(schedule, dueAt);

        assertThat(run.status()).isEqualTo("pending");
        assertThat(run.buildId()).isNull();
        verify(buildService, never()).enqueueScheduled(any());
    }

    private PipelineBuildEntity build(PipelineBuildStatus status, String hash) {
        PipelineBuildEntity build = new PipelineBuildEntity(
                UUID.randomUUID(), pipelineId, versionId, status, PipelineBuildTrigger.SCHEDULE,
                UUID.randomUUID(), schedule.getCreatedBy(), mapper.createArrayNode(),
                mapper.createObjectNode()
        );
        build.updateLogicHash(hash);
        return build;
    }
}
