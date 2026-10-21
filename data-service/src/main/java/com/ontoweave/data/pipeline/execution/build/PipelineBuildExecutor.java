package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStage;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.execution.output.OutputSnapshot;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import com.ontoweave.data.pipeline.execution.output.WrittenSnapshot;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.PreviewPlanBuilder;
import com.ontoweave.data.pipeline.execution.preview.PipelinePreviewService;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class PipelineBuildExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineBuildExecutor.class);
    private static final Duration PREVIEW_RELEASE_TIMEOUT = Duration.ofSeconds(30);

    private final PipelineBuildRepository builds;
    private final PipelineBuildOutputRepository buildOutputs;
    private final PipelineVersionRepository versions;
    private final PipelineOutputRepository outputs;
    private final PipelineDatasetRepository datasets;
    private final PreviewPlanBuilder plans;
    private final PipelineDatasetWriter writer;
    private final ExpectationChecker expectations;
    private final PipelinePublisher publisher;
    private final PipelinePreviewService previews;
    private final ObjectMapper mapper;

    public PipelineBuildExecutor(
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository buildOutputs,
            PipelineVersionRepository versions,
            PipelineOutputRepository outputs,
            PipelineDatasetRepository datasets,
            PreviewPlanBuilder plans,
            PipelineDatasetWriter writer,
            ExpectationChecker expectations,
            PipelinePublisher publisher,
            PipelinePreviewService previews,
            ObjectMapper mapper
    ) {
        this.builds = builds;
        this.buildOutputs = buildOutputs;
        this.versions = versions;
        this.outputs = outputs;
        this.datasets = datasets;
        this.plans = plans;
        this.writer = writer;
        this.expectations = expectations;
        this.publisher = publisher;
        this.previews = previews;
        this.mapper = mapper;
    }

    public void execute(UUID buildId) {
        PipelineBuildEntity build = builds.findById(buildId).orElse(null);
        if (build == null) {
            return;
        }
        if (build.getCancelRequestedAt() != null) {
            cancel(build);
            return;
        }
        if (!releasePreview(build)) {
            fail(build, PipelineBuildConstants.CODE_PREVIEW_BUSY, "预览资源未在限期内释放");
            return;
        }
        try {
            executeOutputs(build);
        } catch (PipelineBuildException error) {
            if (cancelRequested(build)) {
                cancel(build);
            } else {
                fail(build, error.errorCode(), error.getMessage());
            }
        } catch (RuntimeException error) {
            log.error("pipeline build execution failed {}", buildId, error);
            fail(build, PipelineBuildConstants.CODE_EXECUTION_FAILED, "构建执行失败");
        }
    }

    private boolean releasePreview(PipelineBuildEntity build) {
        previews.cancelForBuild(build.getPipelineId());
        return previews.awaitReleased(build.getPipelineId(), PREVIEW_RELEASE_TIMEOUT);
    }

    private void executeOutputs(PipelineBuildEntity build) {
        PipelineVersionEntity version = versions.findById(build.getPipelineVersionId())
                .orElseThrow(() -> new PipelineBuildException(
                        PipelineBuildConstants.CODE_EXECUTION_FAILED, "流程版本不存在"
                ));
        List<ResolvedInput> resolved = PipelineResolvedInputs.fromJson(mapper, build.getResolvedInputs());
        List<PipelineFrozenOutput> frozen = PipelineFrozenOutput.from(version.getOutputs(), mapper);
        for (PipelineFrozenOutput output : frozen) {
            if (cancelRequested(build)) {
                cancel(build);
                return;
            }
            executeOutput(build, version, output, resolved);
        }
        if (cancelRequested(build)) {
            cancel(build);
            return;
        }
        updateStage(build.getId(), PipelineBuildStage.PUBLISHING);
        try {
            publisher.publish(build.getId());
        } catch (PipelineBuildException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_PUBLISH_FAILED, "发布失败", error
            );
        }
    }

    private void executeOutput(
            PipelineBuildEntity build,
            PipelineVersionEntity version,
            PipelineFrozenOutput frozen,
            List<ResolvedInput> resolved
    ) {
        PipelineBuildOutputEntity row = buildOutputs
                .findByBuildIdAndOutputId(build.getId(), frozen.outputId())
                .orElseThrow(() -> new PipelineBuildException(
                        PipelineBuildConstants.CODE_EXECUTION_FAILED, "构建输出记录不存在"
                ));
        Instant started = Instant.now();
        row.markRunning(PipelineBuildStage.SUBMITTING, started);
        buildOutputs.save(row);
        updateStage(build.getId(), PipelineBuildStage.RUNNING);
        WrittenSnapshot snapshot = null;
        try {
            PipelineOutputEntity output = outputs.findById(frozen.outputId())
                    .orElseThrow(() -> new PipelineBuildException(
                            PipelineBuildConstants.CODE_EXECUTION_FAILED, "输出不存在"
                    ));
            LogicalPlan plan = plans.build(version, resolved, output.getNodeId().toString());
            String branch = PipelineBuildConstants.BRANCH_PREFIX + build.getId();
            snapshot = writer.write(build.getId(), frozen.outputId(), plan, branch);
            if (snapshot.engineJobId() != null) {
                row.recordEngineJob(snapshot.engineJobId());
            }
            row.updateStage(PipelineBuildStage.CHECKING_EXPECTATIONS);
            buildOutputs.save(row);
            if (cancelRequested(build)) {
                cancel(build);
                return;
            }
            PipelineDatasetEntity dataset = datasets.findByOutputId(frozen.outputId())
                    .orElseThrow(() -> new PipelineBuildException(
                            PipelineBuildConstants.CODE_EXECUTION_FAILED, "输出数据集不存在"
                    ));
            List<ExpectationResult> results = expectations.check(
                    ExpectationSet.from(dataset.getExpectations()),
                    OutputSnapshot.from(snapshot)
            );
            ObjectNode result = PipelineOutputResultJson.result(mapper, snapshot, results);
            ObjectNode stats = PipelineOutputResultJson.stats(
                    mapper, snapshot, Duration.between(started, Instant.now()).toMillis()
            );
            boolean failed = results.stream().anyMatch(item -> !item.passed());
            if (failed) {
                row.markFailed(
                        PipelineBuildStage.CHECKING_EXPECTATIONS,
                        PipelineBuildConstants.CODE_EXPECTATION_FAILED,
                        "数据期望未通过",
                        result,
                        stats,
                        Instant.now()
                );
                buildOutputs.save(row);
                throw new PipelineBuildException(
                        PipelineBuildConstants.CODE_EXPECTATION_FAILED, "数据期望未通过"
                );
            }
            row.markSucceeded(PipelineBuildStage.FINISHED, result, stats, Instant.now());
            buildOutputs.save(row);
        } catch (PipelineBuildException error) {
            if (cancelRequested(build)) {
                cancel(build);
                return;
            }
            if (row.getStatus() != PipelineOutputStatus.FAILED) {
                row.markFailed(
                        PipelineBuildStage.FINISHED,
                        error.errorCode(),
                        error.getMessage(),
                        PipelineOutputResultJson.result(mapper, snapshot, List.of()),
                        PipelineOutputResultJson.stats(
                                mapper, snapshot, Duration.between(started, Instant.now()).toMillis()
                        ),
                        Instant.now()
                );
                buildOutputs.save(row);
            }
            throw error;
        } catch (RuntimeException error) {
            if (cancelRequested(build)) {
                cancel(build);
                return;
            }
            if (row.getStatus() != PipelineOutputStatus.FAILED) {
                row.markFailed(
                        PipelineBuildStage.FINISHED,
                        PipelineBuildConstants.CODE_EXECUTION_FAILED,
                        "输出执行失败",
                        PipelineOutputResultJson.result(mapper, snapshot, List.of()),
                        PipelineOutputResultJson.stats(
                                mapper, snapshot, Duration.between(started, Instant.now()).toMillis()
                        ),
                        Instant.now()
                );
                buildOutputs.save(row);
            }
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_EXECUTION_FAILED, "输出执行失败", error
            );
        }
    }

    private boolean cancelRequested(PipelineBuildEntity build) {
        PipelineBuildEntity current = builds.findById(build.getId()).orElse(build);
        return current.getCancelRequestedAt() != null;
    }

    private void cancel(PipelineBuildEntity build) {
        writer.cancel(build.getId());
        PipelineBuildEntity current = builds.findById(build.getId()).orElse(build);
        Instant now = Instant.now();
        for (PipelineBuildOutputEntity row : buildOutputs.findByBuildIdOrderByCreatedAtAsc(current.getId())) {
            if (row.getStatus() == PipelineOutputStatus.RUNNING
                    || row.getStatus() == PipelineOutputStatus.QUEUED) {
                row.markCanceled(now);
                buildOutputs.save(row);
            }
        }
        current.markCanceled(now);
        builds.save(current);
    }

    private void updateStage(UUID buildId, PipelineBuildStage stage) {
        builds.updateStage(buildId, stage, com.ontoweave.data.pipeline.domain.PipelineBuildStatus.RUNNING);
    }

    private void fail(PipelineBuildEntity build, String code, String message) {
        PipelineBuildEntity current = builds.findById(build.getId()).orElse(build);
        if (current.getStatus() == com.ontoweave.data.pipeline.domain.PipelineBuildStatus.CANCELED) {
            return;
        }
        Instant now = Instant.now();
        for (PipelineBuildOutputEntity row
                : buildOutputs.findByBuildIdOrderByCreatedAtAsc(current.getId())) {
            if (row.getStatus() == PipelineOutputStatus.RUNNING
                    || row.getStatus() == PipelineOutputStatus.QUEUED) {
                row.markFailed(
                        PipelineBuildStage.FINISHED, code, message,
                        row.getResult(), row.getStats(), now
                );
                buildOutputs.save(row);
            }
        }
        current.markFailed(code, message, now);
        builds.save(current);
    }
}
