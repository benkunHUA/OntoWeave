package com.ontoweave.data.pipeline.check;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
import com.ontoweave.data.pipeline.graph.DraftNode;
import com.ontoweave.data.pipeline.graph.InputSchema;
import com.ontoweave.data.pipeline.graph.InputSchemaResolver;
import com.ontoweave.data.pipeline.graph.OutputConfiguration;
import com.ontoweave.data.pipeline.graph.PipelineDraftGraph;
import com.ontoweave.data.pipeline.graph.PipelineGraphValidator;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PipelineCheckServiceImpl implements PipelineCheckService {

    private final PipelineRepository pipelines;
    private final PipelineDraftRepository drafts;
    private final PipelineOutputRepository outputs;
    private final PipelineDatasetRepository datasets;
    private final PipelineNodeCatalog catalog;
    private final PipelineGraphValidator validator;
    private final InputSchemaResolver inputSchemas;
    private final PipelineStageResolver stages;
    private final NextActionBuilder nextActions = new NextActionBuilder();

    public PipelineCheckServiceImpl(
            PipelineRepository pipelines,
            PipelineDraftRepository drafts,
            PipelineOutputRepository outputs,
            PipelineDatasetRepository datasets,
            PipelineNodeCatalog catalog,
            PipelineGraphValidator validator,
            InputSchemaResolver inputSchemas,
            PipelineStageResolver stages
    ) {
        this.pipelines = pipelines;
        this.drafts = drafts;
        this.outputs = outputs;
        this.datasets = datasets;
        this.catalog = catalog;
        this.validator = validator;
        this.inputSchemas = inputSchemas;
        this.stages = stages;
    }

    @Override
    @Transactional(readOnly = true)
    public PipelineCheckResult check(UUID pipelineId, UUID ownerId, long revision) {
        PipelineEntity pipeline = pipelines.findByIdAndOwnerId(pipelineId, ownerId)
                .orElseThrow(() -> notFound());
        PipelineDraftEntity draft = drafts.findById(pipelineId)
                .orElseThrow(() -> notFound());
        if (draft.getRevision() != revision) {
            throw revisionConflict();
        }
        PipelineDraftGraph graph = PipelineDraftGraph.from(draft.getDefinition());
        Map<UUID, InputSchema> resolvedInputs = resolveInputs(ownerId, graph);
        List<PipelineOutputEntity> activeOutputs = outputs.findByPipelineIdAndArchivedAtIsNull(pipelineId);
        Set<UUID> outputIds = activeOutputs.stream().map(PipelineOutputEntity::getId).collect(Collectors.toSet());
        Map<UUID, OutputConfiguration> outputConfigurations = outputConfigurations(activeOutputs);

        List<PipelineIssue> issues = validator.validate(
                graph,
                catalog,
                resolvedInputs,
                outputIds,
                outputConfigurations
        );
        boolean hasInput = graph.nodes().stream().anyMatch(node -> "input".equals(node.type()));
        boolean hasOutput = graph.nodes().stream().anyMatch(node -> "output".equals(node.type()));
        boolean blocking = issues.stream().anyMatch(PipelineIssue::blocking);
        PipelineStage stage = stages.resolve(
                pipeline,
                revision,
                graph.nodes().isEmpty(),
                hasInput,
                hasOutput,
                blocking
        );
        return new PipelineCheckResult(
                pipelineId,
                revision,
                stage,
                issues.stream().filter(PipelineIssue::blocking).toList(),
                issues.stream().filter(issue -> !issue.blocking()).toList(),
                nextActions.build(stage, graph, issues)
        );
    }

    private Map<UUID, InputSchema> resolveInputs(UUID ownerId, PipelineDraftGraph graph) {
        Set<UUID> datasetIds = graph.nodes().stream()
                .filter(node -> "input".equals(node.type()))
                .map(DraftNode::config)
                .filter(config -> config != null
                        && InputSchemaResolver.SYNC_DATASET_KIND.equals(config.path("datasetKind").asText()))
                .map(config -> uuid(config.path("datasetId").asText(null)))
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (datasetIds.isEmpty()) {
            return Map.of();
        }
        return inputSchemas.resolveAll(ownerId, datasetIds);
    }

    private Map<UUID, OutputConfiguration> outputConfigurations(List<PipelineOutputEntity> activeOutputs) {
        Set<UUID> outputIds = activeOutputs.stream()
                .map(PipelineOutputEntity::getId)
                .collect(Collectors.toSet());
        if (outputIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PipelineDatasetEntity> datasetsByOutput = datasets.findByOutputIdIn(outputIds).stream()
                .collect(Collectors.toMap(PipelineDatasetEntity::getOutputId, dataset -> dataset));
        Map<UUID, OutputConfiguration> configurations = new LinkedHashMap<>();
        for (PipelineOutputEntity output : activeOutputs) {
            PipelineDatasetEntity dataset = datasetsByOutput.get(output.getId());
            configurations.put(output.getId(), new OutputConfiguration(
                    output.getId(),
                    output.getNodeId(),
                    dataset != null,
                    dataset == null ? null : dataset.getPrimaryKeyColumns(),
                    dataset == null ? null : dataset.getExpectations()
            ));
        }
        return configurations;
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private ApiException revisionConflict() {
        return new PipelineRevisionConflict();
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "流程或草稿不存在");
    }
}
