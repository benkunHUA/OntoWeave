package com.ontoweave.data.ontology.edit.service;

import com.ontoweave.data.ontology.edit.model.EditSetPreview;
import com.ontoweave.data.ontology.edit.store.JdbcRelationEditStore;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import com.ontoweave.data.ontology.query.repository.JdbcObjectQuery;
import com.ontoweave.data.ontology.query.repository.JdbcRelationQuery;
import com.ontoweave.data.ontology.query.repository.RelationDefinitionRow;
import com.ontoweave.data.ontology.query.repository.RelationVersionRow;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RelationPreviewBuilder {

    private final JdbcRelationEditStore relationStore;
    private final JdbcRelationQuery relationQuery;
    private final JdbcObjectQuery objectQuery;
    private final EditContextResolver contexts;

    public RelationPreviewBuilder(
            JdbcRelationEditStore relationStore,
            JdbcRelationQuery relationQuery,
            JdbcObjectQuery objectQuery,
            EditContextResolver contexts
    ) {
        this.relationStore = relationStore;
        this.relationQuery = relationQuery;
        this.objectQuery = objectQuery;
        this.contexts = contexts;
    }

    EditSetPreview.RelationChange change(
            UUID ownerId,
            UUID relationId,
            String fromPrimaryKey,
            String toPrimaryKey,
            boolean create,
            Map<String, Long> expected,
            List<EditSetPreview.Conflict> conflicts
    ) {
        if (relationId == null || blank(fromPrimaryKey) || blank(toPrimaryKey)) {
            throw QueryErrors.invalidRequest("关系端点不能为空");
        }
        RelationDefinitionRow definition = relationQuery.findDefinition(relationId, ownerId)
                .orElse(null);
        RelationVersionRow version = relationQuery.findCurrentVersion(relationId, ownerId)
                .orElse(null);
        if (definition == null || version == null) {
            conflicts.add(conflict("RELATION_NOT_INDEXED", "关系没有可用索引", relationId));
            return new EditSetPreview.RelationChange(
                    create ? "RELATION_CREATE" : "RELATION_DELETE",
                    relationId, null, fromPrimaryKey, null, toPrimaryKey, false, false, 0
            );
        }
        if (!"MANY_TO_MANY".equals(version.relationKind())) {
            conflicts.add(conflict("UNSUPPORTED_RELATION_KIND",
                    "外键关系不能直接创建或删除", relationId));
        }
        if (!objectExists(ownerId, definition.sourceTypeId(), fromPrimaryKey)
                || !objectExists(ownerId, definition.targetTypeId(), toPrimaryKey)) {
            conflicts.add(conflict("OBJECT_NOT_FOUND", "关系端点不存在或不可见", relationId));
        }
        JdbcRelationEditStore.RelationEditRow edit = relationStore.findEdit(
                ownerId, relationId, definition.sourceTypeId(), fromPrimaryKey,
                definition.targetTypeId(), toPrimaryKey
        ).orElse(null);
        long actualVersion = edit == null || edit.stale() ? 0 : edit.version();
        Long expectedVersion = expected.get(
                relationKey(relationId, fromPrimaryKey, toPrimaryKey));
        if (expectedVersion != null && expectedVersion != actualVersion) {
            conflicts.add(conflict("EDIT_CONFLICT", "关系版本已变化", relationId,
                    Map.of("expectedVersion", expectedVersion, "actualVersion", actualVersion)));
        }
        boolean beforeExists = effectiveRelationExists(
                version, definition, fromPrimaryKey, toPrimaryKey, edit);
        return new EditSetPreview.RelationChange(
                create ? "RELATION_CREATE" : "RELATION_DELETE",
                relationId, definition.sourceTypeId(), fromPrimaryKey,
                definition.targetTypeId(), toPrimaryKey, beforeExists, create, actualVersion
        );
    }

    private boolean effectiveRelationExists(
            RelationVersionRow version,
            RelationDefinitionRow definition,
            String fromPrimaryKey,
            String toPrimaryKey,
            JdbcRelationEditStore.RelationEditRow edit
    ) {
        if (edit != null && !edit.stale()) {
            return "ACTIVE".equals(edit.state());
        }
        List<Boolean> rows = relationQuery.findRelationInstances(
                version.id(), version.relationId(),
                definition.sourceTypeId(), fromPrimaryKey, definition.targetTypeId(), toPrimaryKey
        );
        return !rows.isEmpty() && rows.get(0);
    }

    private boolean objectExists(UUID ownerId, UUID objectTypeId, String primaryKey) {
        EditContextResolver.EditContext context = contexts
                .find(ownerId, objectTypeId).orElse(null);
        return context != null && objectQuery
                .findDetail(context.versionId(), ownerId, objectTypeId, primaryKey).isPresent();
    }

    private EditSetPreview.Conflict conflict(
            String code,
            String message,
            UUID subjectId
    ) {
        return new EditSetPreview.Conflict(code, message, Map.of("id", subjectId.toString()));
    }

    private EditSetPreview.Conflict conflict(
            String code,
            String message,
            UUID subjectId,
            Map<String, Object> details
    ) {
        Map<String, Object> all = new HashMap<>(details);
        all.put("id", subjectId.toString());
        return new EditSetPreview.Conflict(code, message, all);
    }

    private String relationKey(UUID relationId, String fromPrimaryKey, String toPrimaryKey) {
        return relationId + ":" + fromPrimaryKey + ":" + toPrimaryKey;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
