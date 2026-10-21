package com.ontoweave.data.pipeline.api.dto;

import com.ontoweave.data.pipeline.execution.preview.InputVersion;
import com.ontoweave.data.pipeline.execution.preview.PreviewIssue;
import com.ontoweave.data.sync.iceberg.PreviewColumn;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PreviewResponse(
        UUID previewId,
        UUID pipelineId,
        long draftRevision,
        String nodeId,
        List<PreviewColumn> columns,
        List<Map<String, Object>> rows,
        int returnedRows,
        Long scannedRows,
        boolean truncated,
        String truncationReason,
        long elapsedMs,
        List<InputVersion> inputVersions,
        List<PreviewIssue> errors,
        List<PreviewIssue> warnings
) {

    public PreviewResponse {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
        inputVersions = inputVersions == null ? List.of() : List.copyOf(inputVersions);
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
