package com.ontoweave.data.pipeline.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.api.dto.SourceBuild;
import com.ontoweave.data.pipeline.api.dto.SourceInput;
import com.ontoweave.data.pipeline.api.dto.SourceOutput;
import com.ontoweave.data.pipeline.api.dto.SourcePipelineVersion;
import com.ontoweave.data.pipeline.api.dto.SourceResponse;
import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineBuildOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetVersionEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetVersionRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.ontoweave.data.pipeline.service.PipelineErrors.notFound;

@Service
public class PipelineSourceService {

    private final PipelineRepository pipelines;
    private final PipelineDatasetRepository datasets;
    private final PipelineDatasetVersionRepository datasetVersions;
    private final PipelineBuildOutputRepository buildOutputs;
    private final PipelineBuildRepository builds;
    private final PipelineVersionRepository pipelineVersions;
    private final DatasetMetadataQuery metadataQuery;

    public PipelineSourceService(
            PipelineRepository pipelines,
            PipelineDatasetRepository datasets,
            PipelineDatasetVersionRepository datasetVersions,
            PipelineBuildOutputRepository buildOutputs,
            PipelineBuildRepository builds,
            PipelineVersionRepository pipelineVersions,
            DatasetMetadataQuery metadataQuery
    ) {
        this.pipelines = pipelines;
        this.datasets = datasets;
        this.datasetVersions = datasetVersions;
        this.buildOutputs = buildOutputs;
        this.builds = builds;
        this.pipelineVersions = pipelineVersions;
        this.metadataQuery = metadataQuery;
    }

    @Transactional(readOnly = true)
    public SourceResponse get(UUID pipelineId, UUID datasetId, UUID versionId, UUID ownerId) {
        requirePipeline(pipelineId, ownerId);
        PipelineDatasetEntity dataset = datasets.findByIdAndPipelineId(datasetId, pipelineId)
                .orElseThrow(() -> notFound("输出数据集不存在"));
        UUID selectedVersionId = versionId == null ? dataset.getCurrentVersionId() : versionId;
        if (selectedVersionId == null) {
            throw notFound("数据集版本不存在或未发布");
        }
        PipelineDatasetVersionEntity version = datasetVersions
                .findByIdAndDatasetId(selectedVersionId, datasetId)
                .filter(item -> item.getPublishedAt() != null)
                .orElseThrow(() -> notFound("数据集版本不存在或未发布"));
        PipelineBuildOutputEntity buildOutput = buildOutputs.findById(version.getBuildOutputId())
                .filter(item -> Objects.equals(item.getOutputId(), dataset.getOutputId()))
                .orElseThrow(() -> notFound("来源信息不存在"));
        PipelineBuildEntity build = builds.findById(buildOutput.getBuildId())
                .filter(item -> Objects.equals(item.getPipelineId(), pipelineId))
                .orElseThrow(() -> notFound("来源信息不存在"));
        PipelineVersionEntity pipelineVersion = pipelineVersions
                .findByIdAndPipelineId(build.getPipelineVersionId(), pipelineId)
                .orElseThrow(() -> notFound("来源信息不存在"));

        List<ResolvedSourceInput> resolvedInputs = parseResolvedInputs(build.getResolvedInputs());
        Map<DatasetRef, DatasetMetadata> metadata = resolvedInputs.isEmpty()
                ? Map.of()
                : metadataQuery.batchLoad(
                        ownerId,
                        resolvedInputs.stream().map(ResolvedSourceInput::ref).toList()
                );
        List<SourceInput> inputs = resolvedInputs.stream()
                .map(input -> toSourceInput(input, metadata.get(input.ref())))
                .toList();

        return new SourceResponse(
                pipelineId,
                datasetId,
                version.getId(),
                new SourceOutput(
                        dataset.getOutputId(),
                        dataset.getName(),
                        version.getVersionNumber(),
                        version.getRowCount(),
                        version.getPublishedAt()
                ),
                new SourceBuild(
                        build.getId(),
                        lower(build.getStatus()),
                        lower(build.getTriggerType()),
                        build.getStartedAt(),
                        build.getFinishedAt(),
                        build.getLogUri()
                ),
                new SourcePipelineVersion(
                        pipelineVersion.getId(),
                        pipelineVersion.getVersionNumber(),
                        pipelineVersion.getCreatedAt()
                ),
                inputs
        );
    }

    private void requirePipeline(UUID pipelineId, UUID ownerId) {
        if (pipelineId == null || ownerId == null) {
            throw notFound("流程不存在");
        }
        pipelines.findByIdAndOwnerId(pipelineId, ownerId)
                .orElseThrow(() -> notFound("流程不存在"));
    }

    private List<ResolvedSourceInput> parseResolvedInputs(JsonNode resolvedInputs) {
        if (resolvedInputs == null || !resolvedInputs.isArray()) {
            return List.of();
        }
        Map<DatasetRef, ResolvedSourceInput> unique = new LinkedHashMap<>();
        for (JsonNode item : resolvedInputs) {
            ResolvedSourceInput parsed = parseResolvedInput(item);
            if (parsed == null) {
                return List.of();
            }
            unique.putIfAbsent(parsed.ref(), parsed);
        }
        return new ArrayList<>(unique.values());
    }

    private ResolvedSourceInput parseResolvedInput(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String kind = text(item, "datasetKind");
        UUID datasetId = uuid(text(item, "datasetId"));
        UUID datasetVersionId = uuid(text(item, "datasetVersionId"));
        String schemaHash = text(item, "schemaHash");
        if (kind == null || kind.isBlank() || datasetId == null || datasetVersionId == null
                || schemaHash == null || schemaHash.isBlank()) {
            return null;
        }
        Integer versionNumber = integer(item, "datasetVersionNumber");
        DisplaySnapshot snapshot = parseSnapshot(item.path("displaySnapshot"));
        return new ResolvedSourceInput(
                new DatasetRef(kind, datasetId),
                datasetVersionId,
                versionNumber,
                schemaHash,
                snapshot
        );
    }

    private DisplaySnapshot parseSnapshot(JsonNode value) {
        if (value == null || !value.isObject()) {
            return null;
        }
        return new DisplaySnapshot(
                text(value, "datasetName"),
                integer(value, "datasetVersionNumber"),
                uuid(text(value, "connectionId")),
                text(value, "connectionName"),
                text(value, "sourceSchemaName"),
                text(value, "sourceTableName")
        );
    }

    private SourceInput toSourceInput(ResolvedSourceInput input, DatasetMetadata metadata) {
        DatasetMetadata safeMetadata = metadata == null ? DatasetMetadata.missing() : metadata;
        MetadataStatus status = safeMetadata.status() == null
                ? MetadataStatus.MISSING
                : safeMetadata.status();
        DisplaySnapshot snapshot = input.displaySnapshot();
        String datasetName = null;
        String connectionName = null;
        String sourceSchemaName = null;
        String sourceTableName = null;
        if (status != MetadataStatus.RESTRICTED) {
            datasetName = pick(snapshot == null ? null : snapshot.datasetName(), safeMetadata.datasetName());
            connectionName = pick(
                    snapshot == null ? null : snapshot.connectionName(),
                    safeMetadata.connectionName()
            );
            sourceSchemaName = pick(
                    snapshot == null ? null : snapshot.sourceSchemaName(),
                    safeMetadata.sourceSchemaName()
            );
            sourceTableName = pick(
                    snapshot == null ? null : snapshot.sourceTableName(),
                    safeMetadata.sourceTableName()
            );
        }
        Integer versionNumber = input.datasetVersionNumber() != null
                ? input.datasetVersionNumber()
                : snapshot == null ? null : snapshot.datasetVersionNumber();
        return new SourceInput(
                input.ref().datasetKind(),
                input.ref().datasetId(),
                input.datasetVersionId(),
                versionNumber,
                input.schemaHash(),
                datasetName,
                connectionName,
                sourceSchemaName,
                sourceTableName,
                status
        );
    }

    private String pick(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? null : value.asText();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.asInt();
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private String lower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

}
