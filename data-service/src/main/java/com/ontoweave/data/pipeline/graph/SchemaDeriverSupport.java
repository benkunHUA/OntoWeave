package com.ontoweave.data.pipeline.graph;

import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineIssueCodes;

import java.util.List;

abstract class SchemaDeriverSupport {

    protected final String nodeId;
    protected final List<PipelineIssue> issues;

    protected SchemaDeriverSupport(String nodeId, List<PipelineIssue> issues) {
        this.nodeId = nodeId;
        this.issues = issues;
    }

    protected final FieldSchema field(List<FieldSchema> fields, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return fields.stream().filter(field -> field.name().equals(name)).findFirst().orElse(null);
    }

    protected final void incomplete(String field) {
        issues.add(issue(
                PipelineIssueCodes.NODE_CONFIG_INCOMPLETE,
                field,
                "节点配置不完整: " + field,
                "补全节点配置"
        ));
    }

    protected final void fieldNotFound(String field) {
        issues.add(issue(
                PipelineIssueCodes.FIELD_NOT_FOUND,
                field,
                "字段不存在: " + field,
                "选择上游已有字段"
        ));
    }

    protected final PipelineIssue issue(String code, String field, String message, String hint) {
        return new PipelineIssue(
                code,
                PipelineIssueCodes.PHASE_SCHEMA,
                true,
                nodeId,
                null,
                field,
                message,
                hint
        );
    }
}
