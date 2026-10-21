package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.check.PipelineCheckResult;
import com.ontoweave.data.pipeline.check.PipelineCheckService;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineStage;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineBuildTrigger;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PipelineBuildServiceTest {

    private static final UUID PIPELINE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final long REVISION = 7L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineRepository pipelines = mock(PipelineRepository.class);
    private final PipelineCheckService checks = mock(PipelineCheckService.class);
    private final PipelineVersionFreezer freezer = mock(PipelineVersionFreezer.class);
    private final PipelineBuildRepository builds = mock(PipelineBuildRepository.class);
    private final PipelineBuildOutputRepository outputs = mock(PipelineBuildOutputRepository.class);
    private final PipelineBuildCreator creator = mock(PipelineBuildCreator.class);
    private final PipelineVersionRepository versions = mock(PipelineVersionRepository.class);
    private final PipelineBuildMapper responseMapper = mock(PipelineBuildMapper.class);
    private final PipelineBuildService service = new PipelineBuildServiceImpl(
            pipelines, versions, checks, freezer, builds, outputs, creator, responseMapper, mapper
    );

    @BeforeEach
    void ownsPipeline() {
        PipelineEntity pipeline = mock(PipelineEntity.class);
        when(pipelines.findByIdAndOwnerIdAndArchivedAtIsNull(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(pipeline));
        when(pipelines.findByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(pipeline));
        when(pipelines.lockActiveByIdAndOwnerId(PIPELINE_ID, OWNER_ID))
                .thenReturn(Optional.of(pipeline));
    }

    @Test
    void returnsExistingBuildForSameOperationIdWithoutRevalidatingRevision() {
        PipelineBuildEntity existing = build(QUEUED);
        when(builds.findByPipelineIdAndOperationId(PIPELINE_ID, OPERATION_ID))
                .thenReturn(Optional.of(existing));
        when(outputs.findByBuildIdOrderByCreatedAtAsc(existing.getId())).thenReturn(List.of());
        BuildResponse expected = response(existing, List.of());
        when(responseMapper.toResponse(existing, List.of())).thenReturn(expected);

        BuildResponse response = service.enqueue(
                PIPELINE_ID, OWNER_ID, new CreateBuildRequest(REVISION, OPERATION_ID)
        );

        assertThat(response).isSameAs(expected);
        verifyNoInteractions(checks, freezer);
        verify(builds, never()).saveAndFlush(any());
    }

    @Test
    void rejectsBlockingCheckBeforeCreatingBuild() {
        when(checks.check(PIPELINE_ID, OWNER_ID, REVISION)).thenReturn(new PipelineCheckResult(
                PIPELINE_ID,
                REVISION,
                PipelineStage.INVALID,
                List.of(new PipelineIssue(
                        "OUTPUT_REQUIRED", "check", true, null, null, null, "缺少输出", null
                )),
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> service.enqueue(
                PIPELINE_ID, OWNER_ID, new CreateBuildRequest(REVISION, OPERATION_ID)
        )).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(error.code()).isEqualTo("PIPELINE_NOT_READY");
        });

        verifyNoInteractions(freezer);
        verify(builds, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSecondActiveBuild() {
        when(checks.check(PIPELINE_ID, OWNER_ID, REVISION)).thenReturn(ready());
        when(builds.existsByPipelineIdAndStatusIn(PIPELINE_ID, List.of(
                PipelineBuildStatus.QUEUED, PipelineBuildStatus.RUNNING
        ))).thenReturn(true);

        assertThatThrownBy(() -> service.enqueue(
                PIPELINE_ID, OWNER_ID, new CreateBuildRequest(REVISION, OPERATION_ID)
        )).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(error.code()).isEqualTo("BUILD_ACTIVE");
        });

        verifyNoInteractions(freezer);
    }

    @Test
    void freezesVersionAndCreatesQueuedManualBuildWithOutputRows() {
        PipelineVersionEntity version = version();
        ResolvedInput input = resolvedInput();
        when(checks.check(PIPELINE_ID, OWNER_ID, REVISION)).thenReturn(ready());
        when(builds.existsByPipelineIdAndStatusIn(any(), anyList())).thenReturn(false);
        when(freezer.freeze(PIPELINE_ID, OWNER_ID, REVISION))
                .thenReturn(new FrozenBuild(version, List.of(input)));
        when(creator.create(any(PipelineBuildEntity.class), any(PipelineVersionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(responseMapper.toResponse(any(PipelineBuildEntity.class), anyList()))
                .thenReturn(mock(BuildResponse.class));

        service.enqueue(PIPELINE_ID, OWNER_ID, new CreateBuildRequest(REVISION, OPERATION_ID));

        verify(creator).create(any(PipelineBuildEntity.class), any(PipelineVersionEntity.class));
    }

    @Test
    void scheduledBuildUsesVersionAndResolvedInputsWithoutDraftValidation() {
        PipelineVersionEntity version = version();
        ResolvedInput input = resolvedInput();
        when(pipelines.findByIdAndArchivedAtIsNull(PIPELINE_ID))
                .thenReturn(Optional.of(mock(PipelineEntity.class)));
        when(versions.findByIdAndPipelineId(version.getId(), PIPELINE_ID))
                .thenReturn(Optional.of(version));
        when(builds.findByPipelineIdAndOperationId(PIPELINE_ID, OPERATION_ID)).thenReturn(Optional.empty());
        when(builds.existsByPipelineIdAndStatusIn(any(), anyList())).thenReturn(false);
        when(creator.createInCurrentTransaction(
                any(PipelineBuildEntity.class), any(PipelineVersionEntity.class)
        ))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID buildId = service.enqueueScheduled(new EnqueueScheduledBuild(
                PIPELINE_ID,
                version.getId(),
                OWNER_ID,
                OPERATION_ID,
                List.of(input),
                "logic-hash"
        ));

        assertThat(buildId).isNotNull();
        verifyNoInteractions(checks, freezer);
        verify(creator).createInCurrentTransaction(
                any(PipelineBuildEntity.class), any(PipelineVersionEntity.class)
        );
        verify(creator, never()).create(any(PipelineBuildEntity.class), any(PipelineVersionEntity.class));
    }

    @Test
    void scheduledBuildDoesNotSwallowDatabaseConflicts() {
        PipelineVersionEntity version = version();
        when(pipelines.findByIdAndArchivedAtIsNull(PIPELINE_ID))
                .thenReturn(Optional.of(mock(PipelineEntity.class)));
        when(versions.findByIdAndPipelineId(version.getId(), PIPELINE_ID))
                .thenReturn(Optional.of(version));
        when(builds.findByPipelineIdAndOperationId(PIPELINE_ID, OPERATION_ID)).thenReturn(Optional.empty());
        when(builds.existsByPipelineIdAndStatusIn(any(), anyList())).thenReturn(false);
        when(creator.createInCurrentTransaction(
                any(PipelineBuildEntity.class), any(PipelineVersionEntity.class)
        )).thenThrow(new DataIntegrityViolationException("conflict"));

        assertThatThrownBy(() -> service.enqueueScheduled(new EnqueueScheduledBuild(
                PIPELINE_ID, version.getId(), OWNER_ID, OPERATION_ID, List.of(), "logic-hash"
        ))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    void cancelOnlyWritesCancelRequest() {
        PipelineBuildEntity build = build(RUNNING);
        when(builds.lockByIdAndPipelineId(build.getId(), PIPELINE_ID)).thenReturn(Optional.of(build));

        service.requestCancel(PIPELINE_ID, build.getId(), OWNER_ID);

        assertThat(build.getCancelRequestedAt()).isNotNull();
        assertThat(build.getStatus()).isEqualTo(PipelineBuildStatus.RUNNING);
        verify(builds).save(build);
    }

    private PipelineCheckResult ready() {
        return new PipelineCheckResult(
                PIPELINE_ID, REVISION, PipelineStage.READY, List.of(), List.of(), List.of()
        );
    }

    private static final PipelineBuildStatus QUEUED = PipelineBuildStatus.QUEUED;
    private static final PipelineBuildStatus RUNNING = PipelineBuildStatus.RUNNING;

    private PipelineBuildEntity build(PipelineBuildStatus status) {
        ObjectNode stats = mapper.createObjectNode();
        return new PipelineBuildEntity(
                UUID.randomUUID(),
                PIPELINE_ID,
                UUID.randomUUID(),
                status,
                PipelineBuildTrigger.MANUAL,
                OPERATION_ID,
                OWNER_ID,
                mapper.createArrayNode(),
                stats
        );
    }

    private PipelineVersionEntity version() {
        ObjectNode definition = mapper.createObjectNode();
        ObjectNode output = mapper.createObjectNode()
                .put("outputId", UUID.randomUUID().toString())
                .put("nodeId", UUID.randomUUID().toString())
                .put("outputType", "dataset")
                .put("name", "orders_out")
                .put("writeMode", "snapshot");
        output.putArray("primaryKeyColumns");
        output.putArray("expectations");
        return new PipelineVersionEntity(
                UUID.randomUUID(),
                PIPELINE_ID,
                3,
                definition,
                mapper.createArrayNode().add(output),
                "definition-hash",
                mapper.createArrayNode(),
                REVISION,
                OWNER_ID
        );
    }

    private ResolvedInput resolvedInput() {
        return new ResolvedInput(
                "sync", UUID.randomUUID(), UUID.randomUUID(), 4, "schema-hash",
                "ontoweave", "sales", "orders", 100L, null
        );
    }

    private BuildResponse response(PipelineBuildEntity build, List<BuildOutputResponse> buildOutputs) {
        return new BuildResponse(
                build.getId(), PIPELINE_ID, build.getPipelineVersionId(),
                "queued", null, "manual", null, null, null, null, buildOutputs
        );
    }
}
