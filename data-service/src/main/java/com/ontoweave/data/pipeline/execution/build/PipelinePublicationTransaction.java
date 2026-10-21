package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PipelinePublicationTransaction {

    private final PipelineBuildRepository builds;
    private final PipelineBuildOutputRepository buildOutputs;
    private final PipelineDatasetRepository datasets;
    private final PipelineDatasetVersionRepository versions;
    private final PipelineRepository pipelines;
    private final OutboxWriter outbox;
    private final ObjectMapper mapper;

    public PipelinePublicationTransaction(
            PipelineBuildRepository builds,
            PipelineBuildOutputRepository buildOutputs,
            PipelineDatasetRepository datasets,
            PipelineDatasetVersionRepository versions,
            PipelineRepository pipelines,
            OutboxWriter outbox,
            ObjectMapper mapper
    ) {
        this.builds = builds;
        this.buildOutputs = buildOutputs;
        this.datasets = datasets;
        this.versions = versions;
        this.pipelines = pipelines;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public Publication publish(UUID buildId) {
        PipelineBuildEntity build = builds.lockById(buildId)
                .orElseThrow(() -> new PipelineBuildException(
                        PipelineBuildConstants.CODE_PUBLISH_FAILED, "构建不存在"
                ));
        if (build.getCancelRequestedAt() != null) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_CANCELED, "构建已请求取消"
            );
        }
        if (build.getStatus() != PipelineBuildStatus.RUNNING) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_PUBLISH_FAILED, "构建不在可发布状态"
            );
        }
        List<PipelineBuildOutputEntity> rows = buildOutputs.findByBuildIdOrderByCreatedAtAsc(buildId);
        if (rows.isEmpty() || rows.stream().anyMatch(row -> row.getStatus() != PipelineOutputStatus.SUCCEEDED)) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_PUBLISH_FAILED,
                    "存在未成功的输出，不能发布"
            );
        }
        PipelineEntity pipeline = pipelines.lockById(build.getPipelineId())
                .orElseThrow(() -> new PipelineBuildException(
                        PipelineBuildConstants.CODE_PUBLISH_FAILED, "流程不存在"
                ));

        Instant now = Instant.now();
        List<PublishedDataset> published = new ArrayList<>();
        List<Map<String, Object>> outputPayloads = new ArrayList<>();
        for (PipelineBuildOutputEntity row : rows) {
            PipelineDatasetEntity dataset = datasets.findByOutputId(row.getOutputId())
                    .orElseThrow(() -> new PipelineBuildException(
                            PipelineBuildConstants.CODE_PUBLISH_FAILED,
                            "输出数据集不存在: " + row.getOutputId()
                    ));
            Snapshot snapshot = snapshot(row.getResult());
            int versionNumber = nextVersionNumber(dataset.getId());
            PipelineDatasetVersionEntity version = versions.findByBuildOutputId(row.getId())
                    .orElseGet(() -> new PipelineDatasetVersionEntity(
                            UUID.randomUUID(),
                            dataset.getId(),
                            row.getId(),
                            versionNumber,
                            snapshot.catalog(),
                            snapshot.namespace(),
                            snapshot.table(),
                            snapshot.snapshotId(),
                            snapshot.schema(),
                            snapshot.rowCount(),
                            snapshot.byteSize(),
                            snapshot.committedAt()
                    ));
            version.attachStoragePath(snapshot.storagePath());
            version.markPublished(now);
            versions.save(version);
            if (datasets.activateVersion(dataset.getId(), version.getId()) != 1) {
                throw new PipelineBuildException(
                        PipelineBuildConstants.CODE_PUBLISH_FAILED,
                        "输出数据集不存在: " + dataset.getId()
                );
            }
            String branch = snapshot.branch() == null
                    ? PipelineBuildConstants.BRANCH_PREFIX + buildId
                    : snapshot.branch();
            published.add(new PublishedDataset(dataset.getId(), branch, snapshot.snapshotId()));
            outputPayloads.add(Map.of(
                    "outputId", row.getOutputId(),
                    "datasetId", dataset.getId(),
                    "datasetVersionId", version.getId(),
                    "icebergSnapshotId", snapshot.snapshotId()
            ));
        }

        if (pipeline.getArchivedAt() == null
                && pipelines.activateVersion(build.getPipelineId(), build.getPipelineVersionId()) != 1) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_PUBLISH_FAILED, "流程不存在"
            );
        }
        build.markSucceeded(now);
        builds.save(build);
        outbox.write(
                PipelineBuildConstants.AGGREGATE_PIPELINE,
                build.getPipelineId(),
                PipelineBuildConstants.EVENT_SUCCEEDED,
                Map.of(
                        "buildId", buildId,
                        "pipelineId", build.getPipelineId(),
                        "conversationId", pipeline.getConversationId(),
                        "pipelineVersionId", build.getPipelineVersionId(),
                        "outputs", outputPayloads
                )
        );
        return new Publication(build.getPipelineId(), published);
    }

    private int nextVersionNumber(UUID datasetId) {
        return versions.findTopByDatasetIdOrderByVersionNumberDesc(datasetId)
                .map(value -> value.getVersionNumber() + 1)
                .orElse(1);
    }

    private Snapshot snapshot(JsonNode result) {
        JsonNode value = result == null ? null : result.get("snapshot");
        if (value == null || !value.isObject()) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_PUBLISH_FAILED, "输出缺少 Iceberg 快照"
            );
        }
        try {
            return new Snapshot(
                    value.path("icebergCatalog").asText("ontoweave"),
                    value.path("icebergNamespace").asText(null),
                    value.path("icebergTable").asText(null),
                    value.path("icebergSnapshotId").asLong(),
                    value.path("branch").asText(null),
                    value.hasNonNull("schema") ? value.get("schema") : mapper.createObjectNode(),
                    value.path("rowCount").asLong(),
                    value.path("byteSize").asLong(),
                    Instant.parse(value.path("committedAt").asText(Instant.now().toString())),
                    value.path("storagePath").asText(null)
            );
        } catch (RuntimeException error) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_PUBLISH_FAILED, "输出快照元数据无效", error
            );
        }
    }

    public record Publication(UUID pipelineId, List<PublishedDataset> datasets) {

        public Publication {
            datasets = datasets == null ? List.of() : List.copyOf(datasets);
        }
    }

    public record PublishedDataset(UUID datasetId, String branch, long snapshotId) {
    }

    private record Snapshot(
            String catalog,
            String namespace,
            String table,
            long snapshotId,
            String branch,
            JsonNode schema,
            long rowCount,
            long byteSize,
            Instant committedAt,
            String storagePath
    ) {
    }
}
