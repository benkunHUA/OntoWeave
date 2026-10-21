package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.pipeline.api.dto.PreviewRequest;
import com.ontoweave.data.pipeline.api.dto.PreviewResponse;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.PipelineExecutionLimits;
import com.ontoweave.data.sync.iceberg.PreviewColumn;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PreviewResponseMapper {

    private PreviewResponseMapper() {
    }

    static PreviewResponse response(
            UUID pipelineId,
            PreviewRequest request,
            LogicalPlan plan,
            ExecutionResult result,
            long elapsedMs
    ) {
        int returnedRows = Math.min(result.rows().size(), PipelineExecutionLimits.OUTPUT_ROW_LIMIT);
        boolean truncated = result.truncated()
                || result.rows().size() > PipelineExecutionLimits.OUTPUT_ROW_LIMIT;
        String truncationReason = truncated
                ? "OUTPUT_ROW_LIMIT"
                : result.scanLimited() ? "SCAN_ROW_LIMIT" : null;
        List<InputVersion> inputVersions = plan.inputVersions().stream()
                .map(InputVersion::from)
                .toList();
        return new PreviewResponse(
                request.requestId(),
                pipelineId,
                request.draftRevision(),
                request.nodeId(),
                result.columns(),
                result.rows().subList(0, returnedRows),
                returnedRows,
                result.scannedRows(),
                truncated || result.scanLimited(),
                truncationReason,
                elapsedMs,
                inputVersions,
                result.errors(),
                result.warnings()
        );
    }

    record ExecutionResult(
            List<PreviewColumn> columns,
            List<Map<String, Object>> rows,
            Long scannedRows,
            boolean scanLimited,
            boolean truncated,
            List<PreviewIssue> errors,
            List<PreviewIssue> warnings
    ) {
    }
}
