package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.graph.OutputNodeIds;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PipelineOutputLifecycle {

    private final PipelineOutputRepository outputs;
    private final PipelineDatasetRepository datasets;

    PipelineOutputLifecycle(
            PipelineOutputRepository outputs,
            PipelineDatasetRepository datasets
    ) {
        this.outputs = outputs;
        this.datasets = datasets;
    }

    void reconcile(UUID pipelineId, JsonNode definition) {
        Set<UUID> outputNodeIds = OutputNodeIds.from(definition);
        archiveRemoved(pipelineId, outputNodeIds);
        activateRestored(pipelineId, outputNodeIds);
    }

    private void archiveRemoved(UUID pipelineId, Set<UUID> outputNodeIds) {
        List<PipelineOutputEntity> removed = outputs
                .findByPipelineIdAndArchivedAtIsNull(pipelineId)
                .stream()
                .filter(output -> !outputNodeIds.contains(output.getNodeId()))
                .toList();
        if (removed.isEmpty()) {
            return;
        }
        List<UUID> outputIds = removed.stream().map(PipelineOutputEntity::getId).toList();
        removed.forEach(PipelineOutputEntity::archive);
        outputs.saveAll(removed);
        List<PipelineDatasetEntity> activeDatasets = datasets.findByOutputIdIn(outputIds)
                .stream()
                .filter(dataset -> !dataset.isArchived())
                .toList();
        activeDatasets.forEach(PipelineDatasetEntity::archive);
        datasets.saveAll(activeDatasets);
    }

    private void activateRestored(UUID pipelineId, Set<UUID> outputNodeIds) {
        if (outputNodeIds.isEmpty()) {
            return;
        }
        List<PipelineOutputEntity> restored = outputs
                .findByPipelineIdAndNodeIdIn(pipelineId, outputNodeIds)
                .stream()
                .filter(PipelineOutputEntity::isArchived)
                .toList();
        if (restored.isEmpty()) {
            return;
        }
        List<UUID> outputIds = restored.stream().map(PipelineOutputEntity::getId).toList();
        restored.forEach(PipelineOutputEntity::activate);
        outputs.saveAll(restored);
        List<PipelineDatasetEntity> archivedDatasets = datasets.findByOutputIdIn(outputIds)
                .stream()
                .filter(PipelineDatasetEntity::isArchived)
                .toList();
        archivedDatasets.forEach(PipelineDatasetEntity::activate);
        datasets.saveAll(archivedDatasets);
    }
}
