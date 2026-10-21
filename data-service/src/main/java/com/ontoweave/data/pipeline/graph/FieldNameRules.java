package com.ontoweave.data.pipeline.graph;

import com.ontoweave.data.pipeline.PreviewColumnNames;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.List;

final class FieldNameRules {

    private FieldNameRules() {
    }

    static boolean rejectReserved(String nodeId, String field, List<PipelineIssue> issues) {
        if (!PreviewColumnNames.isReserved(field)) {
            return false;
        }
        issues.add(new PipelineIssue(
                PipelineIssueCodes.INVALID_FIELD_NAME,
                PipelineIssueCodes.PHASE_SCHEMA,
                true,
                nodeId,
                null,
                field,
                "字段名使用了预览保留前缀: " + field,
                "使用不以 " + PreviewColumnNames.HIDDEN_PREFIX + " 开头的字段名"
        ));
        return true;
    }

    static void rejectReservedFields(
            String nodeId,
            List<FieldSchema> fields,
            List<PipelineIssue> issues
    ) {
        if (fields == null) {
            return;
        }
        fields.forEach(field -> rejectReserved(nodeId, field.name(), issues));
    }
}
