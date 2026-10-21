package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDraftEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.draft.PipelineRevisionConflict;
import com.ontoweave.data.pipeline.graph.DraftNode;
import com.ontoweave.data.pipeline.graph.PipelineDraftGraph;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineDraftRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import com.ontoweave.data.pipeline.repository.PipelineVersionRepository;
import com.ontoweave.data.pipeline.service.PipelineErrors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PipelineVersionFreezerImpl implements PipelineVersionFreezer {

    private final PipelineRepository pipelines;
    private final PipelineDraftRepository drafts;
    private final PipelineOutputRepository outputs;
    private final PipelineDatasetRepository datasets;
    private final PipelineVersionRepository versions;
    private final PipelineDefinitionHasher hasher;
    private final PipelineInputResolver inputs;
    private final ObjectMapper mapper;

    public PipelineVersionFreezerImpl(
            PipelineRepository pipelines,
            PipelineDraftRepository drafts,
            PipelineOutputRepository outputs,
            PipelineDatasetRepository datasets,
            PipelineVersionRepository versions,
            PipelineDefinitionHasher hasher,
            PipelineInputResolver inputs,
            ObjectMapper mapper
    ) {
        this.pipelines = pipelines;
        this.drafts = drafts;
        this.outputs = outputs;
        this.datasets = datasets;
        this.versions = versions;
        this.hasher = hasher;
        this.inputs = inputs;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public FrozenBuild freeze(UUID pipelineId, UUID ownerId, long draftRevision) {
        PipelineEntity pipeline = pipelines.lockActiveByIdAndOwnerId(pipelineId, ownerId)
                .orElse(null);
        if (pipeline == null) {
            if (pipelines.findByIdAndOwnerId(pipelineId, ownerId).isPresent()) {
                throw PipelineErrors.archived();
            }
            throw notFound("流程不存在");
        }
        PipelineDraftEntity draft = drafts.findByIdForUpdate(pipelineId)
                .orElseThrow(() -> notFound("流程草稿不存在"));
        if (draft.getRevision() != draftRevision) {
            throw new PipelineRevisionConflict();
        }

        JsonNode definition = draft.getDefinition().deepCopy();
        JsonNode frozenOutputs = outputs(pipelineId);
        JsonNode inputRefs = inputRefs(definition);
        String hash = hasher.normalizedHash(definition, frozenOutputs, inputRefs);

        Optional<PipelineVersionEntity> existing = versions
                .findByPipelineIdAndDefinitionHash(pipelineId, hash);
        if (existing.isPresent()) {
            PipelineVersionEntity version = existing.get();
            return new FrozenBuild(version, inputs.resolve(version));
        }

        int versionNumber = versions.findTopByPipelineIdOrderByVersionNumberDesc(pipelineId)
                .map(value -> value.getVersionNumber() + 1)
                .orElse(1);
        PipelineVersionEntity version = new PipelineVersionEntity(
                UUID.randomUUID(),
                pipeline.getId(),
                versionNumber,
                definition,
                frozenOutputs,
                hash,
                inputRefs,
                draftRevision,
                ownerId
        );
        versions.saveAndFlush(version);
        return new FrozenBuild(version, inputs.resolve(version));
    }

    private JsonNode outputs(UUID pipelineId) {
        List<PipelineOutputEntity> active = outputs.findByPipelineIdAndArchivedAtIsNull(pipelineId)
                .stream()
                .sorted(Comparator.comparing(PipelineOutputEntity::getId))
                .toList();
        Map<UUID, PipelineDatasetEntity> datasetsByOutput = new HashMap<>();
        if (!active.isEmpty()) {
            Set<UUID> outputIds = active.stream()
                    .map(PipelineOutputEntity::getId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            for (PipelineDatasetEntity dataset : datasets.findByOutputIdIn(outputIds)) {
                datasetsByOutput.put(dataset.getOutputId(), dataset);
            }
        }
        ArrayNode array = mapper.createArrayNode();
        for (PipelineOutputEntity output : active) {
            PipelineDatasetEntity dataset = datasetsByOutput.get(output.getId());
            ObjectNode item = array.addObject();
            item.put("outputId", output.getId().toString());
            item.put("nodeId", output.getNodeId().toString());
            item.put("outputType", output.getOutputType().name().toLowerCase());
            item.put("name", output.getName());
            item.put("writeMode", dataset == null
                    ? "snapshot" : dataset.getWriteMode().name().toLowerCase());
            item.set("primaryKeyColumns", dataset == null
                    ? mapper.createArrayNode() : dataset.getPrimaryKeyColumns().deepCopy());
            item.set("expectations", dataset == null
                    ? mapper.createArrayNode() : dataset.getExpectations().deepCopy());
        }
        return array;
    }

    private JsonNode inputRefs(JsonNode definition) {
        PipelineDraftGraph graph = PipelineDraftGraph.from(definition);
        Map<String, ObjectNode> refs = new LinkedHashMap<>();
        for (DraftNode node : graph.nodes()) {
            if (!"input".equals(node.type()) || node.config() == null) {
                continue;
            }
            String datasetKind = node.config().path("datasetKind").asText(null);
            String datasetId = node.config().path("datasetId").asText(null);
            if (datasetKind == null || datasetId == null) {
                continue;
            }
            String key = datasetKind + ":" + datasetId;
            refs.computeIfAbsent(key, ignored -> {
                ObjectNode ref = mapper.createObjectNode();
                ref.put("datasetKind", datasetKind);
                ref.put("datasetId", datasetId);
                return ref;
            });
        }
        ArrayNode array = mapper.createArrayNode();
        refs.values().stream()
                .sorted(Comparator.comparing(ref -> ref.path("datasetKind").asText("")
                        + ":" + ref.path("datasetId").asText("")))
                .forEach(array::add);
        return array;
    }

    private ApiException notFound(String message) {
        return new ApiException(
                HttpStatus.NOT_FOUND, PipelineBuildConstants.CODE_NOT_FOUND, message
        );
    }
}
