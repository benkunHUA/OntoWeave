package com.ontoweave.data.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.api.dto.CopyPipelineRequest;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ontoweave.data.pipeline.service.PipelineErrors.bindingConflict;
import static com.ontoweave.data.pipeline.service.PipelineErrors.notFound;
import static com.ontoweave.data.pipeline.service.PipelineErrors.outputIncomplete;
import static com.ontoweave.data.pipeline.service.PipelineErrors.validation;
import static com.ontoweave.data.pipeline.service.PipelineServiceConstants.ACTOR_TYPE_USER;

@Service
class PipelineCopyService {

    private final PipelineRepository pipelines;
    private final PipelineDraftRepository drafts;
    private final PipelineOutputRepository outputs;
    private final PipelineDatasetRepository datasets;

    PipelineCopyService(
            PipelineRepository pipelines,
            PipelineDraftRepository drafts,
            PipelineOutputRepository outputs,
            PipelineDatasetRepository datasets
    ) {
        this.pipelines = pipelines;
        this.drafts = drafts;
        this.outputs = outputs;
        this.datasets = datasets;
    }

    void copy(PipelineEntity source, UUID ownerId, CopyPipelineRequest request) {
        if (request == null || request.pipelineId() == null || request.conversationId() == null) {
            throw validation("pipelineId 和 conversationId 不能为空");
        }
        String name = PipelineNames.require(request.name() == null ? source.getName() : request.name());
        PipelineDraftEntity sourceDraft = drafts.findByIdForUpdate(source.getId())
                .orElseThrow(() -> notFound("草稿不存在"));
        List<PipelineOutputEntity> sourceOutputs = outputs
                .findByPipelineIdAndArchivedAtIsNull(source.getId());
        Map<UUID, PipelineDatasetEntity> datasetsByOutput = sourceOutputs.isEmpty()
                ? Map.of()
                : datasets.findByOutputIdInAndArchivedAtIsNull(
                                sourceOutputs.stream().map(PipelineOutputEntity::getId).toList()
                        )
                        .stream()
                        .collect(Collectors.toMap(PipelineDatasetEntity::getOutputId, Function.identity()));
        if (sourceOutputs.stream().anyMatch(output -> !datasetsByOutput.containsKey(output.getId()))) {
            throw outputIncomplete();
        }
        Map<UUID, UUID> outputIds = new HashMap<>();
        List<PipelineOutputEntity> copiedOutputs = new ArrayList<>();
        List<PipelineDatasetEntity> copiedDatasets = new ArrayList<>();
        for (PipelineOutputEntity output : sourceOutputs) {
            PipelineDatasetEntity dataset = datasetsByOutput.get(output.getId());
            UUID newOutputId = UUID.randomUUID();
            outputIds.put(output.getId(), newOutputId);
            copiedOutputs.add(new PipelineOutputEntity(
                    newOutputId,
                    request.pipelineId(),
                    output.getNodeId(),
                    output.getOutputType(),
                    output.getName()
            ));
            copiedDatasets.add(new PipelineDatasetEntity(
                    UUID.randomUUID(),
                    newOutputId,
                    request.pipelineId(),
                    dataset.getName(),
                    dataset.getPrimaryKeyColumns(),
                    dataset.getExpectations()
            ));
        }

        JsonNode copiedDefinition = rewriteOutputIds(sourceDraft.getDefinition(), outputIds);
        if (pipelines.findById(request.pipelineId()).isPresent()
                || pipelines.findByConversationId(request.conversationId()).isPresent()) {
            throw bindingConflict();
        }
        if (pipelines.insertIfAbsent(
                request.pipelineId(), ownerId, request.conversationId(), name
        ) == 0) {
            throw bindingConflict();
        }
        outputs.saveAll(copiedOutputs);
        datasets.saveAll(copiedDatasets);
        drafts.save(new PipelineDraftEntity(
                request.pipelineId(),
                copiedDefinition,
                ACTOR_TYPE_USER,
                ownerId
        ));
    }

    private JsonNode rewriteOutputIds(JsonNode definition, Map<UUID, UUID> outputIds) {
        if (definition == null) {
            throw notFound("草稿不存在");
        }
        JsonNode copy = definition.deepCopy();
        JsonNode nodes = copy.path("nodes");
        if (!(nodes instanceof ArrayNode array)) {
            return copy;
        }
        for (JsonNode node : array) {
            if (!(node instanceof ObjectNode objectNode) || !"output".equals(objectNode.path("type").asText())) {
                continue;
            }
            JsonNode config = objectNode.path("config");
            if (!(config instanceof ObjectNode configObject)) {
                throw outputIncomplete();
            }
            UUID oldOutputId = uuid(configObject.path("outputId").asText(null));
            UUID newOutputId = oldOutputId == null ? null : outputIds.get(oldOutputId);
            if (newOutputId == null) {
                throw outputIncomplete();
            }
            configObject.put("outputId", newOutputId.toString());
        }
        return copy;
    }

    private UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
