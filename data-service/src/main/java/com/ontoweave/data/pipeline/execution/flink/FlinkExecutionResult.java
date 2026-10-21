package com.ontoweave.data.pipeline.execution.flink;

import com.ontoweave.data.pipeline.execution.preview.PreviewIssue;
import com.ontoweave.data.sync.iceberg.PreviewColumn;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FlinkExecutionResult(
        UUID executionId,
        List<PreviewColumn> columns,
        List<Map<String, Object>> rows,
        Long scannedRows,
        boolean scanLimited,
        List<PreviewIssue> errors,
        List<PreviewIssue> warnings
) {

    public FlinkExecutionResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
