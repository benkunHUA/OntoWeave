package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineDatasetWriteMode;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputType;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static com.ontoweave.data.pipeline.draft.DraftConstants.CODE_CONFIG_INVALID;
import static com.ontoweave.data.pipeline.draft.DraftConstants.CODE_OUTPUT_NAME_CONFLICT;
import static com.ontoweave.data.pipeline.draft.DraftConstants.WRITE_MODE_SNAPSHOT;

public class PipelineOutputWriter {

    private static final String DATASET_NAME_CONSTRAINT = "uq_pipeline_dataset_name";

    private final PipelineOutputRepository outputs;
    private final PipelineDatasetRepository datasets;
    private final ObjectMapper mapper;

    public PipelineOutputWriter(
            PipelineOutputRepository outputs,
            PipelineDatasetRepository datasets,
            ObjectMapper mapper
    ) {
        this.outputs = outputs;
        this.datasets = datasets;
        this.mapper = mapper;
    }

    public UUID write(
            UUID pipelineId,
            UUID nodeId,
            String name,
            String writeMode,
            JsonNode primaryKeyColumns,
            JsonNode expectations
    ) {
        String normalizedName = requireName(name);
        PipelineDatasetWriteMode mode = parseWriteMode(writeMode);
        ArrayNode keys = requireArray(primaryKeyColumns, "primaryKeyColumns");
        ArrayNode expected = requireArray(expectations, "expectations");
        PipelineOutputEntity output = outputs.findByPipelineIdAndNodeId(pipelineId, nodeId)
                .orElse(null);
        requireUniqueName(pipelineId, nodeId, normalizedName);
        if (output == null) {
            output = new PipelineOutputEntity(
                    UUID.randomUUID(),
                    pipelineId,
                    nodeId,
                    PipelineOutputType.DATASET,
                    normalizedName
            );
            outputs.saveAndFlush(output);
            saveDataset(new PipelineDatasetEntity(
                    UUID.randomUUID(),
                    output.getId(),
                    pipelineId,
                    normalizedName,
                    keys,
                    expected
            ), normalizedName);
            return output.getId();
        }
        requireDatasetType(output);
        output.rename(normalizedName);
        PipelineDatasetEntity dataset = datasets.findByOutputId(output.getId()).orElse(null);
        if (dataset == null) {
            saveDataset(new PipelineDatasetEntity(
                    UUID.randomUUID(),
                    output.getId(),
                    pipelineId,
                    normalizedName,
                    keys,
                    expected
            ), normalizedName);
        } else {
            dataset.updateConfiguration(normalizedName, mode, keys, expected);
            saveDataset(dataset, normalizedName);
        }
        return output.getId();
    }

    private void requireUniqueName(UUID pipelineId, UUID nodeId, String name) {
        outputs.findByPipelineIdAndName(pipelineId, name)
                .filter(existing -> !existing.getNodeId().equals(nodeId))
                .ifPresent(existing -> {
                    throw outputNameConflict(name);
                });
    }

    private void saveDataset(PipelineDatasetEntity dataset, String name) {
        try {
            datasets.saveAndFlush(dataset);
        } catch (DataIntegrityViolationException error) {
            if (!violatesNameConstraint(error)) {
                throw error;
            }
            throw outputNameConflict(name);
        }
    }

    private boolean violatesNameConstraint(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(DATASET_NAME_CONSTRAINT)) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }

    private ApiException outputNameConflict(String name) {
        return new ApiException(
                HttpStatus.CONFLICT,
                CODE_OUTPUT_NAME_CONFLICT,
                "输出名称已存在: " + name,
                java.util.Map.of("name", name)
        );
    }

    private void requireDatasetType(PipelineOutputEntity output) {
        if (output.getOutputType() != PipelineOutputType.DATASET) {
            throw invalid("输出类型必须是 dataset");
        }
    }

    private PipelineDatasetWriteMode parseWriteMode(String writeMode) {
        String value = writeMode == null || writeMode.isBlank()
                ? WRITE_MODE_SNAPSHOT
                : writeMode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!WRITE_MODE_SNAPSHOT.equals(value)) {
            throw invalid("writeMode 只支持 snapshot");
        }
        return PipelineDatasetWriteMode.SNAPSHOT;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw invalid("输出名称不能为空");
        }
        return name.trim();
    }

    private ArrayNode requireArray(JsonNode value, String field) {
        if (value == null) {
            return mapper.createArrayNode();
        }
        if (!value.isArray()) {
            throw invalid(field + " 必须是数组");
        }
        return ((ArrayNode) value.deepCopy());
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_CONFIG_INVALID, message);
    }

}
